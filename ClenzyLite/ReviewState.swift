import Foundation

enum ReviewSort: String, Codable, CaseIterable { case newest = "Newest", oldest = "Oldest", size = "Size" }
enum ReviewQueue: String, Codable, CaseIterable { case regular = "Regular", starred = "Starred" }
enum AppTheme: String, Codable, CaseIterable { case dark = "Dark", light = "Light", system = "System" }
struct MediaRecord: Equatable {
    let id: String
    let date: Date
    var bytes: Int64?
}
struct ReviewAction: Codable, Equatable {
    let id: String
    let previousRank: Int?
    let trashed: Bool
    let queue: ReviewQueue
}
struct ReviewState: Codable {
    var stars: Set<String> = []
    var pendingTrash: Set<String> = []
    var reviewed: [String: Int] = [:]
    var sequence = 0
    var current: String?
    var undo: [ReviewAction] = []
    var sort: ReviewSort = .newest
    var queue: ReviewQueue = .regular
    var theme: AppTheme = .dark
    var tutorialCount = 0

    func ordered(_ items: [MediaRecord]) -> [MediaRecord] {
        items.filter { !pendingTrash.contains($0.id) && (queue == .starred ? stars.contains($0.id) : !stars.contains($0.id)) }
            .sorted { a, b in
                if a.id == current { return b.id != current }
                if b.id == current { return false }
                let ar = reviewed[a.id], br = reviewed[b.id]
                if ar == nil && br != nil { return true }
                if ar != nil && br == nil { return false }
                if let ar, let br, ar != br { return ar < br }
                switch sort {
                case .size:
                    if a.bytes != b.bytes { return (a.bytes ?? -1) > (b.bytes ?? -1) }
                case .oldest: if a.date != b.date { return a.date < b.date }
                case .newest: if a.date != b.date { return a.date > b.date }
                }
                return a.id < b.id
            }
    }
    mutating func swipe(_ id: String, trash: Bool, items: [MediaRecord]) {
        undo.append(ReviewAction(id: id, previousRank: reviewed[id], trashed: trash, queue: queue))
        undo = Array(undo.suffix(2))
        sequence += 1
        reviewed[id] = sequence
        if trash { pendingTrash.insert(id) }
        tutorialCount = min(3, tutorialCount + 1)
        current = nil
        current = ordered(items).first?.id
    }
    mutating func undoLast() {
        guard let action = undo.popLast() else { return }
        reviewed[action.id] = action.previousRank
        if action.trashed { pendingTrash.remove(action.id) }
        queue = action.queue
        current = action.id
    }
    mutating func reconcile(valid: Set<String>) {
        stars.formIntersection(valid)
        pendingTrash.formIntersection(valid)
        reviewed = reviewed.filter { valid.contains($0.key) }
        undo.removeAll { !valid.contains($0.id) }
        if let current, !valid.contains(current) { self.current = nil }
    }
}
struct ReviewStore {
    let url: URL
    func read() throws -> ReviewState {
        guard FileManager.default.fileExists(atPath: url.path) else { return ReviewState() }
        return try JSONDecoder().decode(ReviewState.self, from: Data(contentsOf: url))
    }
    func save(_ state: ReviewState) throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try JSONEncoder().encode(state).write(to: url, options: .atomic)
    }
}
