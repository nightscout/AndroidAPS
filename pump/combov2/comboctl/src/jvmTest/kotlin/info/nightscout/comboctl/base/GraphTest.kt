package info.nightscout.comboctl.base

import app.aaps.shared.tests.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class GraphTest : TestBase() {

    @Test
    fun checkGraphConstruction() {
        // Check basic graph construction. Create 4 nodes, with values 1 through 4.
        // Connect:
        // - node 1 to node 2
        // - node 2 to nodes 1 and 3
        // - node 3 to node 4
        // - node 4 to node 2
        //
        // Then check (a) the number of nodes in the graph,
        // (b) how many edges each node has, and (c) what
        // nodes the edges lead to.

        Graph<Int, String>().apply {
            val n1 = node(1)
            val n2 = node(2)
            val n3 = node(3)
            val n4 = node(4)
            n1.connectTo(n2, "e12")
            n2.connectTo(n1, "e21")
            n2.connectTo(n3, "e23")
            n3.connectTo(n4, "e34")
            n4.connectTo(n2, "e42")

            // Check number of nodes.
            assertEquals(4, nodes.size)

            // Check number of edges per node.
            assertEquals(1, n1.edges.size)
            assertEquals(2, n2.edges.size)
            assertEquals(1, n3.edges.size)
            assertEquals(1, n4.edges.size)

            // Check the nodes the edges lead to.
            assertSame(n2, n1.edges[0].targetNode)
            assertSame(n1, n2.edges[0].targetNode)
            assertSame(n3, n2.edges[1].targetNode)
            assertSame(n4, n3.edges[0].targetNode)
            assertSame(n2, n4.edges[0].targetNode)
        }
    }

    @Test
    fun checkShortestPath() {
        // Check the result of findShortestPath(). For this,
        // construct a graph with cycles and multiple ways
        // to get from one node to another. This graph has
        // 4 nodes, and one ways to get from node 1 to node 4
        // & _two_ ways from node 4 to 1.
        //
        // Path from node 1 to 4: n1 -> n2 -> n3 -> n4
        // First path from node 4 to 1: n4 -> n3 -> n2 -> n1
        // Second path from node 4 to 1: n4 -> n2 -> n1
        //
        // findShortestPath() should find the second path,
        // since it is the shortest one.

        Graph<Int, String>().apply {
            val n1 = node(1)
            val n2 = node(2)
            val n3 = node(3)
            val n4 = node(4)
            n1.connectTo(n2, "e12")
            n2.connectTo(n1, "e21")
            n2.connectTo(n3, "e23")
            n3.connectTo(n4, "e34")
            n4.connectTo(n2, "e42")
            n4.connectTo(n3, "e43")
            n3.connectTo(n2, "e32")

            val pathFromN1ToN4 = findShortestPath(1, 4)!!
            assertEquals(3, pathFromN1ToN4.size)
            assertEquals("e12", pathFromN1ToN4[0].edgeValue)
            assertEquals(2, pathFromN1ToN4[0].targetNodeValue)
            assertEquals("e23", pathFromN1ToN4[1].edgeValue)
            assertEquals(3, pathFromN1ToN4[1].targetNodeValue)
            assertEquals("e34", pathFromN1ToN4[2].edgeValue)
            assertEquals(4, pathFromN1ToN4[2].targetNodeValue)

            val pathFromN4ToN1 = findShortestPath(4, 1)!!
            assertEquals(2, pathFromN4ToN1.size)
            assertEquals("e42", pathFromN4ToN1[0].edgeValue)
            assertEquals(2, pathFromN4ToN1[0].targetNodeValue)
            assertEquals("e21", pathFromN4ToN1[1].edgeValue)
            assertEquals(1, pathFromN4ToN1[1].targetNodeValue)
        }
    }

    @Test
    fun checkNonExistentShortestPath() {
        // Check what happens when trying to find a shortest path
        // between two nodes that have no path that connects the two.
        // The test graph connects node 1 to nodes 2 and 3, but since
        // the edges are directional, getting from nodes 2 and 3 to
        // node 1 is not possible. Consequently, a path from node 2
        // to node 3 cannot be found. findShortestPath() should
        // detect this and return null.

        Graph<Int, String>().apply {
            val n1 = node(1)
            val n2 = node(2)
            val n3 = node(3)

            n1.connectTo(n2, "e12")
            n1.connectTo(n3, "e13")

            val path = findShortestPath(2, 3)
            assertNull(path)
        }
    }

    @Test
    fun checkShortestPathSearchEdgePredicate() {
        // Check the effect of an edge predicate. Establisch a small
        // 3-node graph with nodes 1,2,3 and add a shortcut from
        // node 1 to node 3. Try to find the shortest path from
        // 1 to 3, without and with a predicate. We expect the
        // predicate to skip the edge that goes from node 1 to 3.

        Graph<Int, String>().apply {
            val n1 = node(1)
            val n2 = node(2)
            val n3 = node(3)

            n1.connectTo(n2, "e12")
            n2.connectTo(n3, "e23")
            n1.connectTo(n3, "e13")

            val pathWithoutPredicate = findShortestPath(1, 3)
            assertNotNull(pathWithoutPredicate)
            assertEquals(1, pathWithoutPredicate.size)
            assertEquals("e13", pathWithoutPredicate[0].edgeValue)
            assertEquals(3, pathWithoutPredicate[0].targetNodeValue)

            val pathWithPredicate = findShortestPath(1, 3) { it != "e13" }
            assertNotNull(pathWithPredicate)
            assertEquals(2, pathWithPredicate.size)
            assertEquals("e12", pathWithPredicate[0].edgeValue)
            assertEquals(2, pathWithPredicate[0].targetNodeValue)
            assertEquals("e23", pathWithPredicate[1].edgeValue)
            assertEquals(3, pathWithPredicate[1].targetNodeValue)
        }
    }
}
