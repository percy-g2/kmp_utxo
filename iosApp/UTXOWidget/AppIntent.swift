//
//  AppIntent.swift
//  UTXOWidget
//
//  Created by Prashant Gahlot on 21/12/25.
//  Copyright © 2025 orgName. All rights reserved.
//

import WidgetKit
import AppIntents

struct ConfigurationAppIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource { "Configuration" }
    static var description: IntentDescription { "This is an example widget." }

    // An example configurable parameter.
    @Parameter(title: "Favorite Emoji", default: "😃")
    var favoriteEmoji: String
}
