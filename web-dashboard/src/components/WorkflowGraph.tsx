import { Background, Controls, MiniMap, ReactFlow, type Edge, type Node } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import type { WorkflowEdgeDecision, WorkflowNodeExecution } from "../types";
import { statusTone } from "../lib/utils";

const toneColor: Record<string, string> = {
  success: "#34d399",
  warning: "#f59e0b",
  danger: "#f87171",
  neutral: "#94a3b8"
};

export function WorkflowGraph({
  nodes,
  edges
}: {
  nodes: WorkflowNodeExecution[];
  edges: WorkflowEdgeDecision[];
}) {
  const uniqueNodes = Array.from(new Map(nodes.map((node) => [node.nodeId, node])).values());
  const flowNodes: Node[] = uniqueNodes.map((node, index) => {
    const tone = statusTone(node.status);
    return {
      id: node.nodeId,
      position: { x: (index % 4) * 230, y: Math.floor(index / 4) * 140 },
      data: {
        label: (
          <div className="min-w-40">
            <div className="text-xs font-semibold uppercase text-slate-200">{node.nodeId}</div>
            <div className="mt-1 text-[10px] text-slate-400">{node.nodeType}</div>
            <div className="mt-2 text-[10px]" style={{ color: toneColor[tone] }}>
              {node.status}
            </div>
          </div>
        )
      },
      style: {
        background: "hsl(220 16% 11%)",
        color: "hsl(210 18% 92%)",
        border: `1px solid ${toneColor[tone]}`,
        borderRadius: 8,
        width: 190
      }
    };
  });

  const flowEdges: Edge[] = edges.map((edge) => ({
    id: edge.id,
    source: edge.fromNode,
    target: edge.toNode,
    animated: edge.selected,
    label: edge.edgeType,
    style: { stroke: edge.selected ? "#14b8a6" : "#64748b" },
    labelStyle: { fill: "#cbd5e1", fontSize: 10 }
  }));

  return (
    <div className="h-[420px] overflow-hidden rounded-md border bg-background">
      <ReactFlow nodes={flowNodes} edges={flowEdges} fitView>
        <Background color="#334155" gap={18} />
        <MiniMap pannable zoomable nodeColor={(node) => String(node.style?.border ?? "#94a3b8")} />
        <Controls />
      </ReactFlow>
    </div>
  );
}
