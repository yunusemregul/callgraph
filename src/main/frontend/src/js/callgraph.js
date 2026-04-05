import vis from "vis-network/standalone/umd/vis-network.min.js";
import options from "./vis-options";
import "@fortawesome/fontawesome-free/css/all.min.css";
import { mountSettingsPanel } from "./SettingsPanel.jsx";

window.JavaBridge = {
    goToSource: (referenceHashCode) => {
    },
    saveAsHtml: (unused) => {
    },
    generateGraph: (unused) => {
    },
    openSettings: (unused) => {
    },
    expandNode: (nodeHashCode) => {
    },
    updateSetting: (keyValue) => {
    }
}

const messageElement = document.getElementById("message");
const networkElement = document.getElementById("network");
const generateMessage = document.getElementById("generateMessage");
const showAllButton = document.getElementById("showAllButton");

const network = new vis.Network(networkElement, {}, options);
let hiddenNodes = new Set();
let expandedCallers = new Set();
let expandedCallees = new Set();
let nodesWithSatellites = new Set();
let selectedNodeId = null;
let pendingExpandDirection = null;
let pendingExpandParent = null;
let rootNodeId = null;
let isGraphFitted = false;
let isGraphGenerated = false;
let isLazyMode = true;

// ─── Satellite expand nodes ───────────────────────────────────────────────────

function showSatellitesForNode(nodeId) {
    if (!isLazyMode) return;

    // Refresh: remove existing satellites for this node first
    removeSatellitesForNode(nodeId);

    const positions = network.getPositions([nodeId]);
    const pos = positions[nodeId];
    if (!pos) return;

    const newNodes = [];
    const newEdges = [];
    const offset = 90;

    if (!expandedCallers.has(nodeId)) {
        newNodes.push({
            id: "__callers__" + nodeId,
            label: "+\nCallers",
            shape: "circle",
            size: 24,
            color: {
                background: "#4A7FC1",
                border: "#2B5FA0",
                highlight: { background: "#5A8FD1", border: "#1A4F90" },
                hover: { background: "#5A8FD1", border: "#1A4F90" }
            },
            font: { color: "#FFF", size: 10, multi: false },
            x: pos.x - offset,
            y: pos.y,
            physics: false
        });
        newEdges.push({
            id: "__edge_callers__" + nodeId,
            from: nodeId,
            to: "__callers__" + nodeId,
            dashes: [4, 4],
            arrows: { to: { enabled: false } },
            color: { color: "#4A7FC1", opacity: 0.6 },
            selectable: false,
            hoverWidth: 0
        });
    }

    if (!expandedCallees.has(nodeId)) {
        newNodes.push({
            id: "__callees__" + nodeId,
            label: "+\nCallees",
            shape: "circle",
            size: 24,
            color: {
                background: "#5BAD6F",
                border: "#3A8D4F",
                highlight: { background: "#6BBD7F", border: "#2A7D3F" },
                hover: { background: "#6BBD7F", border: "#2A7D3F" }
            },
            font: { color: "#FFF", size: 10, multi: false },
            x: pos.x + offset,
            y: pos.y,
            physics: false
        });
        newEdges.push({
            id: "__edge_callees__" + nodeId,
            from: nodeId,
            to: "__callees__" + nodeId,
            dashes: [4, 4],
            arrows: { to: { enabled: false } },
            color: { color: "#5BAD6F", opacity: 0.6 },
            selectable: false,
            hoverWidth: 0
        });
    }

    if (newNodes.length > 0) {
        network.body.data.nodes.add(newNodes);
        network.body.data.edges.add(newEdges);
        nodesWithSatellites.add(nodeId);
    }
}

function removeSatellitesForNode(nodeId) {
    const toRemoveNodes = [];
    const toRemoveEdges = [];

    if (network.body.data.nodes.get("__callers__" + nodeId)) {
        toRemoveNodes.push("__callers__" + nodeId);
        toRemoveEdges.push("__edge_callers__" + nodeId);
    }
    if (network.body.data.nodes.get("__callees__" + nodeId)) {
        toRemoveNodes.push("__callees__" + nodeId);
        toRemoveEdges.push("__edge_callees__" + nodeId);
    }

    if (toRemoveNodes.length > 0) network.body.data.nodes.remove(toRemoveNodes);
    if (toRemoveEdges.length > 0) network.body.data.edges.remove(toRemoveEdges);
    nodesWithSatellites.delete(nodeId);
}

function removeAllSatellites() {
    Array.from(nodesWithSatellites).forEach(nodeId => removeSatellitesForNode(nodeId));
    nodesWithSatellites.clear();
}

function handleSatelliteClick(satelliteId) {
    const isCallers = satelliteId.startsWith("__callers__");
    const direction = isCallers ? "callers" : "callees";
    const parentId = parseInt(satelliteId.replace("__callers__", "").replace("__callees__", ""));

    if (isCallers) expandedCallers.add(parentId);
    else expandedCallees.add(parentId);

    // Remove the clicked satellite immediately, refresh parent satellites
    removeSatellitesForNode(parentId);
    showSatellitesForNode(parentId);

    pendingExpandDirection = direction;
    pendingExpandParent = parentId;
    JavaBridge.expandNode(parentId + "|" + direction);
}

// ─── Network events ───────────────────────────────────────────────────────────

network.on("click", function (params) {
    if (params.nodes.length === 1) {
        const nodeId = params.nodes[0];

        if (typeof nodeId === "string" && nodeId.startsWith("__")) {
            handleSatelliteClick(nodeId);
            return;
        }

        selectedNodeId = nodeId;
        JavaBridge.goToSource(nodeId);
    } else {
        selectedNodeId = null;
    }

    if (params.edges.length === 1) {
        const edgeId = params.edges[0];
        if (typeof edgeId !== "string" || !edgeId.startsWith("__")) {
            JavaBridge.goToSource(edgeId);
        }
    }

    updateButtonVisibility();
});

network.on("stabilizationProgress", function (params) {
    const message = "Stabilization progress: " + Math.round(params.iterations / params.total * 100) + "%";
    showMessage(message);
});

network.on("stabilizationIterationsDone", function () {
    hideMessage();
    isGraphGenerated = true;
    fit();
    showGraphControls();
    if (rootNodeId !== null) {
        showSatellitesForNode(rootNodeId);
    }
});

network.on("fit", () => {
    isGraphFitted = true;
    updateButtonVisibility();
});

network.on("dragEnd", () => {
    isGraphFitted = false;
    updateButtonVisibility();
});

network.on("zoom", () => {
    isGraphFitted = false;
    updateButtonVisibility();
});

// ─── Node visibility ──────────────────────────────────────────────────────────

function hideSelectedNode() {
    if (selectedNodeId !== null) {
        hiddenNodes.add(selectedNodeId);
        removeSatellitesForNode(selectedNodeId);
        network.body.data.nodes.update({id: selectedNodeId, hidden: true});
        selectedNodeId = null;
        updateShowAllButton();
        updateButtonVisibility();
    }
}

function showAllNodes() {
    if (hiddenNodes.size > 0) {
        Array.from(hiddenNodes).forEach(nodeId => {
            network.body.data.nodes.update({id: nodeId, hidden: false});
        });
        hiddenNodes.clear();
        updateShowAllButton();
    }
}

function updateShowAllButton() {
    if (hiddenNodes.size > 0) {
        showAllButton.classList.remove("hidden");
    } else {
        showAllButton.classList.add("hidden");
    }
}

// ─── Button visibility ────────────────────────────────────────────────────────

function updateButtonVisibility() {
    const fitButton = document.querySelector('button[onclick="fit()"]');
    const hideNodeButton = document.querySelector('button[onclick="hideSelectedNode()"]');

    if (fitButton) {
        if (!isGraphGenerated || isGraphFitted) {
            fitButton.classList.add("hidden");
        } else {
            fitButton.classList.remove("hidden");
        }
    }

    if (hideNodeButton) {
        if (selectedNodeId !== null) {
            hideNodeButton.classList.remove("hidden");
        } else {
            hideNodeButton.classList.add("hidden");
        }
    }
}

// ─── Messages ─────────────────────────────────────────────────────────────────

function showMessage(message) {
    if (!message || message.trim() === '') {
        message = "Place your caret on a method, right-click, or use Alt+Shift+E shortcut to generate a call graph.";
    }
    messageElement.innerHTML = message;
    messageElement.classList.remove("hidden");
    networkElement.classList.add("hidden");
}

function hideMessage() {
    messageElement.classList.add("hidden");
    networkElement.classList.remove("hidden");
}

function showGraphControls() {
    for (let el of document.getElementsByClassName("generatedGraphController")) {
        el.classList.remove("hidden");
    }
    updateShowAllButton();
    updateButtonVisibility();
}

function hideGraphControls() {
    for (let el of document.getElementsByClassName("generatedGraphController")) {
        el.classList.add("hidden");
    }
}

// ─── Network updates ──────────────────────────────────────────────────────────

function updateNetwork(data) {
    hideGraphControls();
    removeAllSatellites();
    hiddenNodes.clear();
    expandedCallers.clear();
    expandedCallees.clear();
    nodesWithSatellites.clear();
    selectedNodeId = null;
    pendingExpandDirection = null;
    pendingExpandParent = null;
    rootNodeId = null;
    isGraphFitted = false;
    isGraphGenerated = false;
    updateShowAllButton();
    showMessage("Rendering graph...");
    try {
        const rootNode = data.nodes.find(n => n.shape === "circle");
        if (rootNode) rootNodeId = rootNode.id;
        options.groups = data.groups;
        network.setOptions(options);
        network.setData(data);
        network.stabilize();
    } catch (e) {
        showMessage(e);
    }
}

function addToNetwork(data) {
    if (!data || (!data.nodes.length && !data.edges.length)) return;

    if (data.groups && Object.keys(data.groups).length > 0) {
        const mergedGroups = Object.assign({}, options.groups || {}, data.groups);
        options.groups = mergedGroups;
        network.setOptions({groups: mergedGroups});
    }

    const expandDirection = pendingExpandDirection;
    const expandParent = pendingExpandParent;
    pendingExpandDirection = null;
    pendingExpandParent = null;

    const positionedNodes = positionAroundParent(data.nodes, expandParent, expandDirection);

    network.body.data.nodes.add(positionedNodes);
    network.body.data.edges.add(data.edges);

    positionedNodes.forEach(node => {
        const nodeId = node.id;
        if (expandDirection === "callers") {
            expandedCallees.add(nodeId);
        } else if (expandDirection === "callees") {
            expandedCallers.add(nodeId);
        }
        showSatellitesForNode(nodeId);
    });
}

function positionAroundParent(nodes, parentId, direction) {
    if (!parentId || !nodes.length) return nodes;

    const positions = network.getPositions([parentId]);
    const parentPos = positions[parentId];
    if (!parentPos) return nodes;

    const count = nodes.length;
    const verticalOffset = 160;
    const horizontalSpread = 150;
    const directionSign = direction === "callers" ? -1 : 1;

    return nodes.map((node, i) => {
        const offsetX = count > 1 ? (i - (count - 1) / 2) * horizontalSpread : 0;
        const offsetY = directionSign * verticalOffset;
        return Object.assign({}, node, {
            x: parentPos.x + offsetX,
            y: parentPos.y + offsetY,
            physics: false
        });
    });
}

// ─── Misc ─────────────────────────────────────────────────────────────────────

function reposition() {
    // Remove all satellites, re-run physics to re-layout, then restore satellites
    removeAllSatellites();
    const satelliteOwners = Array.from(nodesWithSatellites); // already cleared, capture from expandedCallers/Callees
    network.setOptions({physics: {enabled: true}});
    network.once("stabilizationIterationsDone", function () {
        network.setOptions({physics: {enabled: false}});
        // Restore satellites for all non-satellite nodes currently in the graph
        network.body.data.nodes.getIds().forEach(nodeId => {
            if (typeof nodeId !== "string") {
                showSatellitesForNode(nodeId);
            }
        });
        fit();
    });
    network.stabilize();
}

function fit() {
    network.fit();
    isGraphFitted = true;
    updateButtonVisibility();
}

const MESSAGE_TYPE_SUCCESS = "+";
const MESSAGE_TYPE_ERROR = "-";

function setGenerateMessage(message) {
    if (!message || message.trim() === '') {
        message = "-PLACE YOUR CARET ON A METHOD";
    }
    let flag = message.substring(0, 1);
    let cls = "navbuttonMessage ";
    if (flag === MESSAGE_TYPE_SUCCESS) cls += "navbuttonMessage-success";
    else if (flag === MESSAGE_TYPE_ERROR) cls += "navbuttonMessage-error";
    generateMessage.className = cls;
    generateMessage.innerHTML = message.substring(1);
}

function updateMessageTextColor(backgroundColor) {
    backgroundColor = backgroundColor.replace('#', '');
    const r = parseInt(backgroundColor.substr(0, 2), 16);
    const g = parseInt(backgroundColor.substr(2, 2), 16);
    const b = parseInt(backgroundColor.substr(4, 2), 16);
    const brightness = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
    messageElement.style.color = brightness > 0.5 ? "black" : "white";
}

// ─── Settings UI (Leva) ───────────────────────────────────────────────────────

// Callbacks wired from SettingsPanel.jsx via window globals
window.__levaLazyMode = function(enabled) {
    isLazyMode = enabled;
};
window.__levaPhysics = function(enabled) {
    network.setOptions({physics: {enabled: enabled}});
};

function setInitialSettings(settings) {
    isLazyMode = settings.lazyExpansion;
    if (window.__levaSet) {
        window.__levaSet({
            'Lazy Mode': settings.lazyExpansion,
            'Max Depth': settings.maxDepth,
            'Direction': settings.graphDirection,
            'Filter Tests': settings.filterTestCode,
        });
    }
}

function setLazyMode(enabled) {
    isLazyMode = enabled;
    if (window.__levaSet) window.__levaSet({'Lazy Mode': enabled});
}

function resetDefaultMessages() {
    showMessage();
    setGenerateMessage();
}

window.updateNetwork = updateNetwork;
window.addToNetwork = addToNetwork;
window.fit = fit;
window.showMessage = showMessage;
window.setGenerateMessage = setGenerateMessage;
window.updateMessageTextColor = updateMessageTextColor;
window.resetDefaultMessages = resetDefaultMessages;
window.hideSelectedNode = hideSelectedNode;
window.showAllNodes = showAllNodes;
window.setLazyMode = setLazyMode;
window.setInitialSettings = setInitialSettings;
window.reposition = reposition;

resetDefaultMessages();
updateButtonVisibility();
mountSettingsPanel('leva-root');
