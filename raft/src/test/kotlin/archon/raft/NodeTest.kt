package archon.raft

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LibraryTest {
    @Test
    fun someLibraryMethodReturnsTrue() {
        val node = Node()
        assertEquals(0, node.state)
        node.inc()
        assertEquals(1, node.state)
    }
}
