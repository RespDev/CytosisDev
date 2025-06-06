package net.cytonic.cytosis.plugins.dependencies;

import com.google.common.collect.Maps;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import net.cytonic.cytosis.plugins.PluginDescription;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles sorting plugin dependencies into an order that satisfies all dependencies.
 */
public class DependencyUtils {

    private DependencyUtils() {
        throw new AssertionError();
    }

    /**
     * Attempts to topographically sort all plugins for the proxy to load in dependency order using a
     * depth-first search.
     *
     * @param candidates the plugins to sort
     * @return the sorted list of plugins
     * @throws IllegalStateException if there is a circular loop in the dependency graph
     */
    public static List<PluginDescription> sortCandidates(List<PluginDescription> candidates) {
        List<PluginDescription> sortedCandidates = new ArrayList<>(candidates);
        sortedCandidates.sort(Comparator.comparing(PluginDescription::getId));

        // Create a graph and populate it with plugin dependencies. Specifically, each graph has plugin
        // nodes, and edges that represent the dependencies that plugin relies on. Non-existent plugins
        // are ignored.
        MutableGraph<PluginDescription> graph = GraphBuilder.directed().allowsSelfLoops(false).expectedNodeCount(sortedCandidates.size()).build();
        Map<String, PluginDescription> candidateMap = Maps.uniqueIndex(sortedCandidates, PluginDescription::getId);

        for (PluginDescription description : sortedCandidates) {
            graph.addNode(description);

            for (PluginDependency dependency : description.getDependencies()) {
                PluginDescription in = candidateMap.get(dependency.getId());

                if (in != null) {
                    graph.putEdge(description, in);
                }
            }
        }

        // Now we do the depth-first search. The most accessible description of the algorithm is on
        // Wikipedia: https://en.wikipedia.org/w/index.php?title=Topological_sorting&oldid=1036420482,
        // section "Depth-first search." Apparently this algorithm originates from "Introduction to
        // Algorithms" (2nd ed.)
        List<PluginDescription> sorted = new ArrayList<>();
        Map<PluginDescription, Mark> marks = new HashMap<>();

        for (PluginDescription node : graph.nodes()) {
            visitNode(graph, node, marks, sorted, new ArrayDeque<>());
        }

        return sorted;
    }

    private static void visitNode(Graph<PluginDescription> dependencyGraph, PluginDescription current, Map<PluginDescription, Mark> visited, List<PluginDescription> sorted, Deque<PluginDescription> currentDependencyScanStack) {
        Mark mark = visited.getOrDefault(current, Mark.NOT_VISITED);
        if (mark == Mark.VISITED) {
            // Visited this node already, nothing to do.
            return;
        } else if (mark == Mark.VISITING) {
            // A circular dependency has been detected. (Specifically, if we are visiting any dependency
            // and a dependency we are looking at depends on any dependency being visited, we have a
            // circular dependency, thus we do not have a directed acyclic graph and therefore no
            // topological sort is possible.)
            currentDependencyScanStack.addLast(current);
            final String loop = currentDependencyScanStack.stream().map(PluginDescription::getId).collect(Collectors.joining(" -> "));
            throw new IllegalStateException("Circular dependency detected: " + loop);
        }

        // Visiting this node. Mark this node as having a visit in progress and scan its edges.
        currentDependencyScanStack.addLast(current);
        visited.put(current, Mark.VISITING);
        for (PluginDescription edge : dependencyGraph.successors(current)) {
            visitNode(dependencyGraph, edge, visited, sorted, currentDependencyScanStack);
        }

        // All other dependency nodes were visited. We are clear to mark as visited and add to the
        // sorted list.
        visited.put(current, Mark.VISITED);
        currentDependencyScanStack.removeLast();
        sorted.add(current);
    }

    private enum Mark {
        NOT_VISITED, VISITING, VISITED
    }
}