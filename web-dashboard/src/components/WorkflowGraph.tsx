import { Background, Controls, MiniMap, ReactFlow, type Edge, type Node } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { statusTone } from "../lib/utils";

const toneColor: Record<string, string> = {
  success: "#34d399",
  warning: "#f59e0b",
  danger: "#f87171",
  neutral: "#94a3b8"
};

export type WorkflowGraphNode = {
  id: string;
  type: string;
  status?: string;
  summary?: string;
};

export type WorkflowGraphEdge = {
  id: string;
  source: string;
  target: string;
  type: string;
  condition?: string;
  reason?: string;
  selected?: boolean;
};

export function WorkflowGraph({
  nodes,
  edges,
  heightClassName = "h-[420px]"
}: {
  nodes: WorkflowGraphNode[];
  edges: WorkflowGraphEdge[];
  heightClassName?: string;
}) {
  const uniqueNodes = Array.from(new Map(nodes.map((node) => [node.id, node])).values());
  const flowNodes: Node[] = uniqueNodes.map((node, index) => {
    const tone = statusTone(node.status ?? "PENDING");
    return {
      id: node.id,
      position: { x: (index % 4) * 230, y: Math.floor(index / 4) * 140 },
      data: {
        label: (
          <div className="min-w-40">
            <div className="text-xs font-semibold uppercase text-slate-200">{node.id}</div>
            <div className="mt-1 text-[10px] text-slate-400">{node.type}</div>
            {node.status ? (
              <div className="mt-2 text-[10px]" style={{ color: toneColor[tone] }}>
                {node.status}
              </div>
            ) : null}
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
    source: edge.source,
    target: edge.target,
    animated: Boolean(edge.selected),
    label: edge.condition || edge.type,
    style: { stroke: edge.selected ? "#14b8a6" : "#64748b" },
    labelStyle: { fill: "#cbd5e1", fontSize: 10 }
  }));

  return (
    <div className={`${heightClassName} overflow-hidden rounded-md border bg-background`}>
      <ReactFlow nodes={flowNodes} edges={flowEdges} fitView>
        <Background color="#334155" gap={18} />
        <MiniMap pannable zoomable nodeColor={(node) => String(node.style?.border ?? "#94a3b8")} />
        <Controls />
      </ReactFlow>
    </div>
  );
}
