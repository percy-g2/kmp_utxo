//
//  UTXOWidgetLiveActivity.swift
//  UTXOWidget
//
//  Created by Prashant Gahlot on 21/12/25.
//  Copyright © 2025 orgName. All rights reserved.
//

import ActivityKit
import WidgetKit
import SwiftUI

struct UTXOWidgetAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        // Dynamic stateful properties about your activity go here!
        var emoji: String
    }

    // Fixed non-changing properties about your activity go here!
    var name: String
}

struct UTXOWidgetLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: UTXOWidgetAttributes.self) { context in
            // Lock screen/banner UI goes here
            VStack {
                Text("Hello \(context.state.emoji)")
            }
            .activityBackgroundTint(Color.cyan)
            .activitySystemActionForegroundColor(Color.black)

        } dynamicIsland: { context in
            DynamicIsland {
                // Expanded UI goes here.  Compose the expanded UI through
                // various regions, like leading/trailing/center/bottom
                DynamicIslandExpandedRegion(.leading) {
                    Text("Leading")
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text("Trailing")
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text("Bottom \(context.state.emoji)")
                    // more content
                }
            } compactLeading: {
                Text("L")
            } compactTrailing: {
                Text("T \(context.state.emoji)")
            } minimal: {
                Text(context.state.emoji)
            }
            .widgetURL(URL(string: "http://www.apple.com"))
            .keylineTint(Color.red)
        }
    }
}

extension UTXOWidgetAttributes {
    fileprivate static var preview: UTXOWidgetAttributes {
        UTXOWidgetAttributes(name: "World")
    }
}

extension UTXOWidgetAttributes.ContentState {
    fileprivate static var smiley: UTXOWidgetAttributes.ContentState {
        UTXOWidgetAttributes.ContentState(emoji: "😀")
     }
     
     fileprivate static var starEyes: UTXOWidgetAttributes.ContentState {
         UTXOWidgetAttributes.ContentState(emoji: "🤩")
     }
}

#Preview("Notification", as: .content, using: UTXOWidgetAttributes.preview) {
   UTXOWidgetLiveActivity()
} contentStates: {
    UTXOWidgetAttributes.ContentState.smiley
    UTXOWidgetAttributes.ContentState.starEyes
}
