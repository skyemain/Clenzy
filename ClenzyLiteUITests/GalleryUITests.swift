import XCTest

final class GalleryUITests: XCTestCase {
    @MainActor func testGalleryResumeUndoMenusAndVideo() throws {
        continueAfterFailure = false
        let app = XCUIApplication()
        addUIInterruptionMonitor(withDescription: "Photos access") { alert in
            let allow = alert.buttons.matching(NSPredicate(format: "label CONTAINS[c] 'Full Access' OR label CONTAINS[c] 'All Photos'")).firstMatch
            if allow.exists { allow.tap(); return true }
            return false
        }
        app.launch()
        if app.buttons["Choose Photos access"].waitForExistence(timeout: 3) {
            app.buttons["Choose Photos access"].tap()
            let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
            let allow = springboard.buttons.matching(NSPredicate(format: "label CONTAINS[c] 'Full Access' OR label CONTAINS[c] 'All Photos'")).firstMatch
            if allow.waitForExistence(timeout: 2) { allow.tap() }
        }
        let surface = app.descendants(matching: .any).matching(identifier: "mediaSurface").firstMatch
        XCTAssertTrue(surface.waitForExistence(timeout: 20), app.debugDescription)
        func id() -> String { surface.value as? String ?? "" }
        func swipe(left: Bool) {
            let start = surface.coordinate(withNormalizedOffset: CGVector(dx: left ? 0.8 : 0.2, dy: 0.35))
            let end = surface.coordinate(withNormalizedOffset: CGVector(dx: left ? 0.2 : 0.8, dy: 0.35))
            start.press(forDuration: 0.05, thenDragTo: end)
        }
        func waitForID(_ expected: String) {
            XCTAssertTrue(NSPredicate(format: "value == %@", expected).evaluate(with: surface) || XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == %@", expected), object: surface)], timeout: 8) == .completed)
        }
        func screenshot(_ name: String) {
            let attachment = XCTAttachment(screenshot: app.screenshot()); attachment.name = name; attachment.lifetime = .keepAlways; add(attachment)
        }
        let initial = id(); XCTAssertFalse(initial.isEmpty)
        screenshot("Gallery")
        swipe(left: false)
        XCTAssertTrue(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: NSPredicate(format: "value != %@", initial), object: surface)], timeout: 8) == .completed)
        let resumed = id()
        app.terminate(); app.launch()
        XCTAssertTrue(surface.waitForExistence(timeout: 20)); waitForID(resumed)
        swipe(left: true)
        let secondTrash = id()
        swipe(left: true)
        XCTAssertFalse(app.alerts.firstMatch.exists, "Queuing must not invoke Photos deletion")
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Undo last swipe'")).firstMatch.tap()
        waitForID(secondTrash)
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Undo last swipe'")).firstMatch.tap()
        waitForID(resumed)
        app.buttons["Star and hide from regular queue"].tap()
        surface.press(forDuration: 1)
        XCTAssertTrue(app.navigationBars["Options"].waitForExistence(timeout: 5))
        screenshot("Options")
        app.buttons["Queue"].tap()
        app.buttons["Starred"].tap()
        app.navigationBars.buttons.firstMatch.tap()
        app.buttons["Done"].tap()
        waitForID(resumed)
        app.terminate(); app.launch()
        XCTAssertTrue(surface.waitForExistence(timeout: 20)); waitForID(resumed)
        surface.press(forDuration: 1)
        app.buttons["Settings"].tap()
        screenshot("Settings")
        app.buttons["Access check"].tap()
        XCTAssertTrue(app.staticTexts["All photos"].waitForExistence(timeout: 5))
        screenshot("Access")
        app.navigationBars.buttons.firstMatch.tap()
        app.navigationBars.buttons.firstMatch.tap()
        app.buttons["Queue"].tap()
        app.buttons["Regular"].tap()
        app.navigationBars.buttons.firstMatch.tap()
        app.buttons["Done"].tap()
        for _ in 0..<8 {
            if app.buttons["Play video"].waitForExistence(timeout: 2) { break }
            swipe(left: false)
        }
        XCTAssertTrue(app.buttons["Play video"].exists)
        XCTAssertFalse(app.sliders["Video position"].exists)
        app.buttons["Play video"].tap()
        XCTAssertTrue(app.sliders["Video position"].waitForExistence(timeout: 15))
        let videoID = id()
        app.sliders["Video position"].adjust(toNormalizedSliderPosition: 0.6)
        waitForID(videoID)
        screenshot("Video playback")
    }
}
