import BackgroundTasks
import Foundation
import UserNotifications

/// BGAppRefresh handler: reads alerts JSON from UserDefaults (written by Kotlin) and polls Binance REST.
/// Does not persist `lastTriggeredAt` into KStore; foreground [AlertEvaluator] remains source of truth for cooldown when the app runs.
enum PriceAlertBackground {
    static let taskIdentifier = "org.androdevlinux.utxo.price-alert-refresh"

    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: taskIdentifier, using: nil) { task in
            guard let refresh = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            handle(refresh)
        }
    }

    static func scheduleNext() {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: taskIdentifier)
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    private static func handle(_ task: BGAppRefreshTask) {
        scheduleNext()

        let ud = UserDefaults.standard
        guard ud.string(forKey: "UTXO_PriceAlertsOn") == "1",
              let json = ud.string(forKey: "UTXO_PriceAlertsJson"),
              !json.isEmpty,
              let data = json.data(using: .utf8),
              let raw = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]
        else {
            task.setTaskCompleted(success: true)
            return
        }

        var expired = false
        task.expirationHandler = { expired = true }

        let symbols = Set(raw.compactMap { $0["symbol"] as? String })
        let group = DispatchGroup()
        var tickerData: [String: (price: Double, pct: Double)] = [:]
        let lock = NSLock()

        for symbol in symbols {
            if expired { break }
            group.enter()
            fetchTicker24h(symbol: symbol) { result in
                defer { group.leave() }
                if let (p, c) = result {
                    lock.lock()
                    tickerData[symbol] = (p, c)
                    lock.unlock()
                }
            }
        }

        group.notify(queue: .global(qos: .utility)) {
            let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
            for item in raw {
                if expired { break }
                guard let id = item["id"] as? String,
                      let isEnabled = item["isEnabled"] as? Bool, isEnabled,
                      let symbol = item["symbol"] as? String,
                      let displayName = item["displayName"] as? String,
                      let condition = item["condition"] as? [String: Any],
                      let type = condition["type"] as? String,
                      let tick = tickerData[symbol]
                else { continue }

                let last = tick.price
                let pct = tick.pct

                let lastTriggeredAny = item["lastTriggeredAt"]
                let lastTriggered: Int64? = {
                    if lastTriggeredAny is NSNull || lastTriggeredAny == nil { return nil }
                    if let x = lastTriggeredAny as? Int64 { return x }
                    if let x = lastTriggeredAny as? Int { return Int64(x) }
                    if let x = lastTriggeredAny as? Double { return Int64(x) }
                    if let n = lastTriggeredAny as? NSNumber { return n.int64Value }
                    return nil
                }()

                let repeatMin = (item["repeatAfterMinutes"] as? Int) ?? 60
                let cooldownMs = Int64(repeatMin) * 60_000
                if let t = lastTriggered, nowMs - t < cooldownMs { continue }

                var fire = false
                switch type {
                case "price_above":
                    if let p = doubleValue(condition["price"]), last > p { fire = true }
                case "price_below":
                    if let p = doubleValue(condition["price"]), last < p { fire = true }
                case "percent_change_up":
                    if let p = doubleValue(condition["percent"]), pct >= p { fire = true }
                case "percent_change_down":
                    if let p = doubleValue(condition["percent"]), pct <= -abs(p) { fire = true }
                default:
                    break
                }
                if fire {
                    postNotification(
                        alertId: id,
                        title: "\(displayName) alert triggered",
                        body: bodyFor(type: type, last: last, pct: pct, condition: condition)
                    )
                }
            }
            task.setTaskCompleted(success: true)
        }
    }

    private static func doubleValue(_ any: Any?) -> Double? {
        if let d = any as? Double { return d }
        if let i = any as? Int { return Double(i) }
        if let n = any as? NSNumber { return n.doubleValue }
        return nil
    }

    private static func bodyFor(type: String, last: Double, pct: Double, condition: [String: Any]) -> String {
        switch type {
        case "price_above", "price_below":
            if let p = doubleValue(condition["price"]) {
                return String(format: "Price %.8f (target %.8f)", last, p)
            }
            return String(format: "Price %.8f", last)
        case "percent_change_up", "percent_change_down":
            return String(format: "24h change %.2f%%", pct)
        default:
            return "Check the app for details."
        }
    }

    private static func fetchTicker24h(symbol: String, completion: @escaping ((Double, Double)?) -> Void) {
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: "&+=?")
        let enc = symbol.addingPercentEncoding(withAllowedCharacters: allowed) ?? symbol
        guard let url = URL(string: "https://api.binance.com/api/v3/ticker/24hr?symbol=\(enc)") else {
            completion(nil)
            return
        }
        URLSession.shared.dataTask(with: url) { data, _, _ in
            guard let data,
                  let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let lastStr = obj["lastPrice"] as? String,
                  let pctStr = obj["priceChangePercent"] as? String,
                  let last = Double(lastStr),
                  let pct = Double(pctStr)
            else {
                completion(nil)
                return
            }
            completion((last, pct))
        }.resume()
    }

    private static func postNotification(alertId: String, title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        let req = UNNotificationRequest(identifier: "utxo-bg-\(alertId)", content: content, trigger: nil)
        UNUserNotificationCenter.current().add(req)
    }
}

final class PriceAlertBgCoordinator {
    private var lastOn = false

    func tick() {
        let on = UserDefaults.standard.string(forKey: "UTXO_PriceAlertsOn") == "1"
        if on, !lastOn {
            PriceAlertBackground.scheduleNext()
        }
        lastOn = on
    }
}
