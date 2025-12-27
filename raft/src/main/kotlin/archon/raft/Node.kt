package archon.raft

class Node {
    var state = 0

    fun inc() {
        this.state += 1
    }
}
