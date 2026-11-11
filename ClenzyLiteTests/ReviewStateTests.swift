import XCTest
@testable import ClenzyLite

final class ReviewStateTests: XCTestCase {
    let items = [MediaRecord(id: "a", date: Date(timeIntervalSince1970: 1), bytes: 30), MediaRecord(id: "b", date: Date(timeIntervalSince1970: 2), bytes: 10), MediaRecord(id: "c", date: Date(timeIntervalSince1970: 3), bytes: nil)]
    func testNewestAndOldest() {
        var s = ReviewState()
        XCTAssertEqual(s.ordered(items).map(\.id), ["c", "b", "a"])
        s.sort = .oldest
        XCTAssertEqual(s.ordered(items).map(\.id), ["a", "b", "c"])
    }
    func testSizeUnknownLast() {
        var s = ReviewState(); s.sort = .size
        XCTAssertEqual(s.ordered(items).map(\.id), ["a", "b", "c"])
    }
    func testResumeAfterNextSurvivesSerialization() throws {
        var s = ReviewState(); s.swipe("c", trash: false, items: items)
        let restored = try JSONDecoder().decode(ReviewState.self, from: JSONEncoder().encode(s))
        XCTAssertEqual(restored.ordered(items).map(\.id), ["b", "a", "c"])
        XCTAssertEqual(restored.current, "b")
    }
    func testTwoTrashUndoRestoresBoth() {
        var s = ReviewState(); s.swipe("c", trash: true, items: items); s.swipe("b", trash: true, items: items)
        XCTAssertEqual(s.pendingTrash, ["b", "c"])
        s.undoLast(); XCTAssertEqual(s.current, "b"); XCTAssertEqual(s.pendingTrash, ["c"])
        s.undoLast(); XCTAssertEqual(s.current, "c"); XCTAssertTrue(s.pendingTrash.isEmpty)
    }
    func testUndoCappedAtTwo() {
        var s = ReviewState()
        for id in ["c", "b", "a"] { s.swipe(id, trash: false, items: items) }
        XCTAssertEqual(s.undo.map(\.id), ["b", "a"])
        s.undoLast(); s.undoLast(); s.undoLast()
        XCTAssertEqual(s.reviewed["c"], 1)
    }
    func testUndoReturnsToOriginalQueue() {
        var s = ReviewState()
        s.swipe("c", trash: true, items: items)
        s.queue = .starred
        s.undoLast()
        XCTAssertEqual(s.queue, .regular)
        XCTAssertEqual(s.ordered(items).first?.id, "c")
    }
    func testStarsExcludedFromRegular() {
        var s = ReviewState(); s.stars = ["b"]
        XCTAssertEqual(s.ordered(items).map(\.id), ["c", "a"])
        s.queue = .starred
        XCTAssertEqual(s.ordered(items).map(\.id), ["b"])
    }
    func testReconcileExternalDeletion() {
        var s = ReviewState(); s.swipe("c", trash: true, items: items); s.stars = ["c"]
        s.reconcile(valid: ["a", "b"])
        XCTAssertTrue(s.pendingTrash.isEmpty); XCTAssertTrue(s.undo.isEmpty); XCTAssertTrue(s.stars.isEmpty)
    }
    func testAtomicPersistence() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString).appendingPathComponent("state.json")
        defer { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
        let store = ReviewStore(url: url)
        var s = ReviewState(); s.swipe("c", trash: true, items: items); s.theme = .light; s.sort = .size
        try store.save(s)
        let restored = try store.read()
        XCTAssertEqual(restored.pendingTrash, ["c"]); XCTAssertEqual(restored.theme, .light); XCTAssertEqual(restored.sort, .size)
    }
}
