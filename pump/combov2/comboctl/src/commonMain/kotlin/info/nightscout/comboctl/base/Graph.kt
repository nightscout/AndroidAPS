package info.nightscout.comboctl.base

/**
 * Simple directed cyclic graph data structure.
 *
 * This class is intended to be used primarily for navigating through
 * the graph and finding shortest paths through it. For this reason,
 * there is an overall collection of all nodes (the [nodes] [Map]),
 * while there is no corresponding overall collection of edges.
 *
 * Edges are all directional. If there is a bidirectional connection
 * between nodes, it must be modeled with two directional edges.
 *
 * Nodes and edges have associated user defined values. Node values
 * must be unique, since they are used as a key in [nodes].
 *
 * Note: Nodes and edges can only be added, not removed. This
 * class isn't intended for graphs that change after construction.
 *
 * Constructing a graph is usually done with [apply], like this:
 *
 * ```
 * val myGraph = Graph<Int, String>().apply {
 *     // Create some nodes with integer values
 *     val node1 = node(10)
 *     val node2 = node(20)
 *     val node3 = node(30)
 *     val node4 = node(40)
 *
 *     // Connect two nodes in one direction (from node1 to node2) by
 *     // creating one edge. The new edge's value is "edge_1_2".
 *     connectDirectionally("edge_1_2", node1, node2)
 *     // Connect two nodes in both directions by creating two edges,
 *     // with the node2->node3 edge's value being "edge_2_3", and
 *     // the node3->node2 edge's value being "edge_3_2".
 *     connectBidirectionally("edge_2_3", "edge_3_2", node2, node3)
 *     // Connect more than 2 nodes in one direction. Two new edges
 *     // are created, node1->node2 and node2->node4. Both edges have
 *     // the same value "edge_N".
 *     connectDirectionally("edge_N", node1, node2, node4)
 * }
 * ```
 */
class Graph<NodeValue, EdgeValue> {
    /**
     * Directional edge in the graph.
     *
     * @param value User defined value associated with this edge.
     * @param targetNode Node this edge leads to.
     */
    inner class Edge(val value: EdgeValue, val targetNode: Node)

    /**
     * Node in the graph.
     *
     * @param value User defined value associated with this node.
     */
    inner class Node(val value: NodeValue) {
        private val _edges = mutableListOf<Edge>()
        val edges: List<Edge> = _edges

        internal fun connectTo(targetNode: Node, edgeValue: EdgeValue): Edge {
            val newEdge = Edge(edgeValue, targetNode)
            _edges.add(newEdge)
            return newEdge
        }
    }

    private val _nodes = mutableMapOf<NodeValue, Node>()
    val nodes: Map<NodeValue, Node> = _nodes

    /**
     * Constructs a new [Node] with the given user defined [value].
     */
    fun node(value: NodeValue): Node {
        val newNode = Node(value)
        _nodes[value] = newNode
        return newNode
    }
}

/**
 * Segment of a path found by [findShortestPath].
 *
 * An edge that is part of the shortest path equals one path segment.
 * [targetNodeValue] is the [Graph.Edge.targetNode] field of that edge,
 * [edgeValue] the user-defined value of that edge.
 */
data class PathSegment<NodeValue, EdgeValue>(val targetNodeValue: NodeValue, val edgeValue: EdgeValue)

/**
 * Convenience [findShortestPath] overload based on user-defined node values.
 *
 * This is a shortcut for accessing "from" and "to" nodes
 * from [Graph.node] based on user-defined node values.
 * If values are specified that are not associated with
 * nodes, [IllegalArgumentException] is thrown.
 */
fun <NodeValue, EdgeValue> Graph<NodeValue, EdgeValue>.findShortestPath(
    from: NodeValue,
    to: NodeValue,
    edgePredicate: (edgeValue: EdgeValue) -> Boolean = { true }
): List<PathSegment<NodeValue, EdgeValue>>? {
    val fromNode = nodes[from] ?: throw IllegalArgumentException()
    val toNode = nodes[to] ?: throw IllegalArgumentException()
    return findShortestPath(fromNode, toNode, edgePredicate)
}

/**
 * Finds the shortest path between two nodes.
 *
 * The path starts at [fromNode] and ends at [toNode]. If no path between
 * these two nodes can be found, null is returned. If [fromNode] and
 * [toNode] are the same, an empty list is returned. If no path can be
 * found, null is returned.
 *
 * The [edgePredicate] decides whether an edge in the graph shall be
 * traversed as part of the search. If the predicate returns false,
 * the edge is skipped. This is useful for filtering out edges if the
 * node they lead to is disabled/invalid for some reason. The predicate
 * takes as its argument the value of the edge. The default predicate
 * always returns true and thus allows all edges to be traversed.
 *
 * @param fromNode Start node of the shortest path.
 * @param toNode End node of the shortest path.
 * @param edgePredicate Predicate to apply to each edge during the search.
 * @return Shortest path, or null if no such path exists.
 */
fun <NodeValue, EdgeValue> Graph<NodeValue, EdgeValue>.findShortestPath(
    fromNode: Graph<NodeValue, EdgeValue>.Node,
    toNode: Graph<NodeValue, EdgeValue>.Node,
    edgePredicate: (edgeValue: EdgeValue) -> Boolean = { true }
): List<PathSegment<NodeValue, EdgeValue>>? {
    if (fromNode === toNode)
        return listOf()

    val visitedNodes = mutableListOf<Graph<NodeValue, EdgeValue>.Node>()
    val path = mutableListOf<PathSegment<NodeValue, EdgeValue>>()

    fun visitAdjacentNodes(node: Graph<NodeValue, EdgeValue>.Node): Boolean {
        if (node in visitedNodes)
            return false

        for (edge in node.edges) {
            if (edgePredicate(edge.value) && (edge.targetNode === toNode)) {
                path.add(0, PathSegment(edge.targetNode.value, edge.value))
                return true
            }
        }

        visitedNodes.add(node)

        for (edge in node.edges) {
            if (edgePredicate(edge.value) && visitAdjacentNodes(edge.targetNode)) {
                path.add(0, PathSegment(edge.targetNode.value, edge.value))
                return true
            }
        }

        return false
    }

    return if (visitAdjacentNodes(fromNode))
        path
    else
        null
}

fun <NodeValue, EdgeValue> connectDirectionally(
    fromToEdgeValue: EdgeValue,
    vararg nodes: Graph<NodeValue, EdgeValue>.Node
) {
    require(nodes.size >= 2)

    for (i in 0 until (nodes.size - 1)) {
        val fromNode = nodes[i]
        val toNode = nodes[i + 1]
        fromNode.connectTo(toNode, fromToEdgeValue)
    }
}

fun <NodeValue, EdgeValue> connectBidirectionally(
    fromToEdgeValue: EdgeValue,
    toFromEdgeValue: EdgeValue,
    vararg nodes: Graph<NodeValue, EdgeValue>.Node
) {
    require(nodes.size >= 2)

    for (i in 0 until (nodes.size - 1)) {
        val fromNode = nodes[i]
        val toNode = nodes[i + 1]
        fromNode.connectTo(toNode, fromToEdgeValue)
        toNode.connectTo(fromNode, toFromEdgeValue)
    }
}

/**
 * Utility function to dump a graph using GraphViz DOT notation.
 *
 * This can be used for visualizing a graph using common GraphViz tools.
 */
fun <NodeValue, EdgeValue> Graph<NodeValue, EdgeValue>.toDotGraph(graphName: String): String {
    var dotGraphText = "strict digraph \"$graphName\" {\n"

    for (node in nodes.values) {
        for (edge in node.edges) {
            val fromNodeValue = node.value
            val toNodeValue = edge.targetNode.value
            dotGraphText += "  \"$fromNodeValue\"->\"$toNodeValue\" [ label=\"${edge.value}\" ]\n"
        }
    }

    dotGraphText += "}\n"

    return dotGraphText
}
