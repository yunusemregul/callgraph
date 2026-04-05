import { useRef, useEffect, useState, useCallback } from 'react'
import vis from 'vis-network/standalone/umd/vis-network.min.js'
import { useControls, Leva, folder } from 'leva'
import visOptions from './vis-options'
import '@fortawesome/fontawesome-free/css/all.min.css'
import './App.css'

window.JavaBridge = {
    goToSource: () => {},
    saveAsHtml: () => {},
    generateGraph: () => {},
    openSettings: () => {},
    expandNode: () => {},
    updateSetting: () => {},
}

export default function App() {
    const containerRef = useRef(null)
    const net = useRef(null)

    // ── React UI state ────────────────────────────────────────────────────────
    const [showNetwork, setShowNetwork] = useState(false)
    const [message, setMessage] = useState('Place your caret on a method, right-click, or use Alt+Shift+E to generate a call graph.')
    const [messageColor, setMessageColor] = useState('white')
    const [genMsg, setGenMsg] = useState({ text: 'PLACE YOUR CARET ON A METHOD', cls: 'gen-message-error' })
    const [isGraphGenerated, setIsGraphGenerated] = useState(false)
    const [isGraphFitted, setIsGraphFitted] = useState(false)
    const [selectedNodeId, setSelectedNodeId] = useState(null)
    const [hasHidden, setHasHidden] = useState(false)
    const [isLazyMode, setIsLazyMode] = useState(false)
    const [needsRegen, setNeedsRegen] = useState(false)
    const [repositionProgress, setRepositionProgress] = useState(null) // null = not repositioning, 0-100 = progress
    const ideBackgroundColor = useRef('#000000')
    const customBackgroundColor = useRef('#000000')

    // ── Leva controls ─────────────────────────────────────────────────────────
    const [, setLevaValues] = useControls(() => ({
        'Graph Settings': folder({
            'Lazy Mode': {
                value: false,
                onChange: (v, _, { initial }) => {
                    if (initial) return
                    setIsLazyMode(v)
                    setNeedsRegen(true)
                    window.__appState.isLazyMode = v
                    window.JavaBridge.updateSetting('lazyExpansion|' + v)
                }
            },
            'Max Depth': {
                value: 5, min: 2, max: 20, step: 1,
                disabled: isLazyMode,
                onChange: (v, _, { initial }) => {
                    if (initial) return
                    setNeedsRegen(true)
                    window.JavaBridge.updateSetting('maxDepth|' + Math.round(v))
                }
            },
            'Direction': {
                value: 'callers',
                options: { Callers: 'callers', Callees: 'callees', Both: 'both' },
                onChange: (v, _, { initial }) => {
                    if (initial) return
                    setNeedsRegen(true)
                    window.JavaBridge.updateSetting('graphDirection|' + v)
                }
            },
            'Filter Out Tests': {
                value: true,
                onChange: (v, _, { initial }) => {
                    if (initial) return
                    setNeedsRegen(true)
                    window.JavaBridge.updateSetting('filterTestCode|' + v)
                }
            },
            'Physics': {
                value: false,
                onChange: (v, _, { initial }) => {
                    if (initial) return
                    net.current?.setOptions({ physics: { enabled: v } })
                }
            },
        }),
        'Appearance': folder({
            'Use IDE Background': {
                value: false,
                onChange: (v, _, { initial }) => {
                    console.log('[UseIDE] onChange fired, v=', v, 'initial=', initial, 'ideColor=', ideBackgroundColor.current, 'customColor=', customBackgroundColor.current)
                    if (initial) return
                    const bg = v ? ideBackgroundColor.current : customBackgroundColor.current
                    console.log('[UseIDE] applying background:', bg)
                    applyBackground(bg)
                    window.JavaBridge.updateSetting('backgroundType|' + (v ? 'ide' : 'custom'))
                }
            },
            'Background Color': {
                value: '#000000',
                render: get => !get('Use IDE Background'),
                onChange: (v, _, { initial }) => {
                    console.log('[BgColor] onChange fired, v=', v, 'initial=', initial)
                    customBackgroundColor.current = v
                    if (initial) return
                    console.log('[BgColor] applying background:', v)
                    applyBackground(v)
                    window.JavaBridge.updateSetting('customBackgroundColor|' + v)
                }
            },
        }, { collapsed: true }),
    }), [isLazyMode])

    // Expose setters so Java and graph logic can push values
    useEffect(() => {
        window.__levaSet = setLevaValues
        window.__setNeedsRegen = setNeedsRegen
        window.__setRepositionProgress = setRepositionProgress
    }, [setLevaValues, setNeedsRegen, setRepositionProgress])

    // ── Graph logic (runs once, all closures capture stable setState fns) ─────
    useEffect(() => {
        const n = new vis.Network(containerRef.current, {}, { ...visOptions })
        net.current = n
        window.__networkInstance = n

        // Mutable graph state (not React state — no re-renders needed)
        const s = {
            hiddenNodes: new Set(),
            expandedCallers: new Set(),
            expandedCallees: new Set(),
            nodesWithSatellites: new Set(),
            pendingExpandDirection: null,
            pendingExpandParent: null,
            rootNodeId: null,
            isLazyMode: false,
            currentGroups: {},
        }

        // Expose state for Leva onChange callbacks
        window.__appState = s

        // ── Satellites ────────────────────────────────────────────────────────

        function showSatellitesForNode(nodeId) {
            removeSatellitesForNode(nodeId)

            const newNodes = [], newEdges = []

            const nodeData = n.body.data.nodes.get(nodeId)
            if (!s.expandedCallers.has(nodeId) && nodeData?.hasCallers) {
                newNodes.push({
                    id: '__callers__' + nodeId, label: '+ callers', shape: 'diamond', size: 18,
                    color: { background: '#1a2a3a', border: '#4A7FC1', highlight: { background: '#1e3248', border: '#6A9FE1' }, hover: { background: '#1e3248', border: '#6A9FE1' } },
                    font: { color: '#7AB3E8', size: 10, bold: true },
                    borderWidth: 2,
                    shadow: { enabled: true, color: 'rgba(74,127,193,0.5)', size: 10, x: 0, y: 0 },
                })
                newEdges.push({ id: '__edge_callers__' + nodeId, from: nodeId, to: '__callers__' + nodeId, length: 60, dashes: [4, 4], arrows: { to: { enabled: false } }, color: { color: '#4A7FC1', opacity: 0.5 }, selectable: false, hoverWidth: 0 })
            }

            if (nodeId === s.rootNodeId && !s.expandedCallees.has(nodeId) && nodeData?.hasCallees) {
                newNodes.push({
                    id: '__callees__' + nodeId, label: '+ callees', shape: 'diamond', size: 18,
                    color: { background: '#1a2e1e', border: '#5BAD6F', highlight: { background: '#1e3823', border: '#7BCF8F' }, hover: { background: '#1e3823', border: '#7BCF8F' } },
                    font: { color: '#7BCF8F', size: 10, bold: true },
                    borderWidth: 2,
                    shadow: { enabled: true, color: 'rgba(91,173,111,0.5)', size: 10, x: 0, y: 0 },
                })
                newEdges.push({ id: '__edge_callees__' + nodeId, from: nodeId, to: '__callees__' + nodeId, length: 60, dashes: [4, 4], arrows: { to: { enabled: false } }, color: { color: '#5BAD6F', opacity: 0.5 }, selectable: false, hoverWidth: 0 })
            }

            if (newNodes.length > 0) {
                n.body.data.nodes.add(newNodes)
                n.body.data.edges.add(newEdges)
                s.nodesWithSatellites.add(nodeId)
            }
        }

        function removeSatellitesForNode(nodeId) {
            const toRemoveNodes = [], toRemoveEdges = []
            if (n.body.data.nodes.get('__callers__' + nodeId)) { toRemoveNodes.push('__callers__' + nodeId); toRemoveEdges.push('__edge_callers__' + nodeId) }
            if (n.body.data.nodes.get('__callees__' + nodeId)) { toRemoveNodes.push('__callees__' + nodeId); toRemoveEdges.push('__edge_callees__' + nodeId) }
            if (toRemoveNodes.length > 0) n.body.data.nodes.remove(toRemoveNodes)
            if (toRemoveEdges.length > 0) n.body.data.edges.remove(toRemoveEdges)
            s.nodesWithSatellites.delete(nodeId)
        }

        function removeAllSatellites() {
            Array.from(s.nodesWithSatellites).forEach(removeSatellitesForNode)
            s.nodesWithSatellites.clear()
        }

        function handleSatelliteClick(satelliteId) {
            const isCallers = satelliteId.startsWith('__callers__')
            const direction = isCallers ? 'callers' : 'callees'
            const parentId = parseInt(satelliteId.replace('__callers__', '').replace('__callees__', ''))
            if (isCallers) s.expandedCallers.add(parentId)
            else s.expandedCallees.add(parentId)
            removeSatellitesForNode(parentId)
            showSatellitesForNode(parentId)
            s.pendingExpandDirection = direction
            s.pendingExpandParent = parentId
            window.JavaBridge.expandNode(parentId + '|' + direction)
        }

        // ── Network events ────────────────────────────────────────────────────

        n.on('click', (params) => {
            if (params.nodes.length === 1) {
                const nodeId = params.nodes[0]
                if (typeof nodeId === 'string' && nodeId.startsWith('__')) {
                    handleSatelliteClick(nodeId)
                    return
                }
                setSelectedNodeId(nodeId)
                window.JavaBridge.goToSource(nodeId)
            } else {
                setSelectedNodeId(null)
            }
            if (params.edges.length === 1) {
                const edgeId = params.edges[0]
                if (typeof edgeId !== 'string' || !edgeId.startsWith('__')) window.JavaBridge.goToSource(edgeId)
            }
        })

        n.on('stabilizationProgress', (params) => {
            setMessage('Stabilization: ' + Math.round(params.iterations / params.total * 100) + '%')
        })

        n.on('stabilizationIterationsDone', () => {
            if (s.rootNodeId !== null) n.body.data.nodes.update({ id: s.rootNodeId, fixed: { x: true, y: true } })
            setShowNetwork(true)
            setIsGraphGenerated(true)
            // Defer fit until after React re-renders the network div (was display:none)
            setTimeout(() => {
                n.fit()
                setIsGraphFitted(true)
            }, 0)
        })

        n.on('fit', () => setIsGraphFitted(true))
        n.on('dragEnd', () => setIsGraphFitted(false))
        n.on('zoom', () => setIsGraphFitted(false))

        // ── Global functions (called by Java via executeJavaScript) ────────────

        window.updateNetwork = (data) => {
            removeAllSatellites()
            s.hiddenNodes.clear()
            s.expandedCallers.clear()
            s.expandedCallees.clear()
            s.nodesWithSatellites.clear()
            s.pendingExpandDirection = null
            s.pendingExpandParent = null
            s.rootNodeId = null
            setSelectedNodeId(null)
            setHasHidden(false)
            setIsGraphFitted(false)
            setIsGraphGenerated(false)
            setNeedsRegen(false)
            setShowNetwork(false)
            setMessage('Rendering graph...')
            try {
                const rootNode = data.nodes.find(nd => nd.shape === 'circle')
                if (rootNode) s.rootNodeId = rootNode.id
                s.currentGroups = data.groups || {}
                const opts = { ...visOptions, groups: data.groups }
                n.setOptions(opts)
                n.setData(data)
                // Mark nodes that already have connections as expanded so satellites don't re-add them
                data.nodes.forEach(node => {
                    const edges = n.getConnectedEdges(node.id)
                    edges.forEach(edgeId => {
                        const edge = n.body.data.edges.get(edgeId)
                        if (!edge || typeof edgeId === 'string') return
                        if (edge.to === node.id) s.expandedCallers.add(node.id)
                        if (edge.from === node.id) s.expandedCallees.add(node.id)
                    })
                })
                // Add satellites before stabilizing so they're part of the simulation
                data.nodes.forEach(node => showSatellitesForNode(node.id))
                n.stabilize()
            } catch (e) {
                setMessage(String(e))
            }
        }

        window.addToNetwork = (data) => {
            if (!data || (!data.nodes.length && !data.edges.length)) return
            if (data.groups && Object.keys(data.groups).length > 0) {
                s.currentGroups = { ...s.currentGroups, ...data.groups }
                n.setOptions({ groups: s.currentGroups })
            }
            const expandDirection = s.pendingExpandDirection
            const expandParent = s.pendingExpandParent
            s.pendingExpandDirection = null
            s.pendingExpandParent = null

            const seeded = seedPositionNearParent(data.nodes, expandParent)

            // Pin all existing nodes (including satellites) so they don't move while new ones settle
            const existingIds = n.body.data.nodes.getIds()
            existingIds.forEach(id => n.body.data.nodes.update({ id, fixed: { x: true, y: true } }))

            n.body.data.nodes.add(seeded)
            n.body.data.edges.add(data.edges)

            // Ensure newly added nodes are not fixed
            seeded.forEach(node => n.body.data.nodes.update({ id: node.id, fixed: { x: false, y: false } }))

            // Only show satellites for newly added nodes
            seeded.forEach(node => showSatellitesForNode(node.id))

            const physicsWasEnabled = n.physics.options.enabled
            n.setOptions({ physics: { enabled: true } })
            if (!physicsWasEnabled) window.__levaSet?.({ 'Physics': true })

            // Kick new nodes after physics is running so velocity isn't reset
            requestAnimationFrame(() => {
                seeded.forEach(node => {
                    const body = n.body.nodes[node.id]
                    if (body) {
                        const angle = Math.random() * 2 * Math.PI
                        body.vx = Math.cos(angle) * 200
                        body.vy = Math.sin(angle) * 200
                    }
                })
            })

            const finishExpansion = () => {
                n.body.data.nodes.getIds().forEach(id => {
                    if (id === s.rootNodeId) return
                    n.body.data.nodes.update({ id, fixed: { x: false, y: false } })
                })
                if (!physicsWasEnabled) {
                    n.setOptions({ physics: { enabled: false } })
                    window.__levaSet?.({ 'Physics': false })
                }
            }

            // Fallback: disable physics after 5s if stabilized never fires
            const fallbackTimer = setTimeout(() => {
                n.off('stabilized', onStabilized)
                finishExpansion()
            }, 5000)

            const onStabilized = () => {
                clearTimeout(fallbackTimer)
                finishExpansion()
            }
            n.once('stabilized', onStabilized)
        }

        window.showMessage = (msg) => {
            if (!msg || !msg.trim()) msg = 'Place your caret on a method, right-click, or use Alt+Shift+E to generate a call graph.'
            setMessage(msg)
            setShowNetwork(false)
        }

        window.setGenerateMessage = (msg) => {
            if (!msg || !msg.trim()) { setGenMsg({ text: 'PLACE YOUR CARET ON A METHOD', cls: 'gen-message-error' }); return }
            const flag = msg[0]
            const text = msg.slice(1)
            setGenMsg({ text, cls: flag === '+' ? 'gen-message-success' : flag === '-' ? 'gen-message-error' : '' })
        }

        window.updateMessageTextColor = (bg) => {
            bg = bg.replace('#', '')
            const r = parseInt(bg.substr(0, 2), 16), g = parseInt(bg.substr(2, 2), 16), b = parseInt(bg.substr(4, 2), 16)
            setMessageColor((0.299 * r + 0.587 * g + 0.114 * b) / 255 > 0.5 ? 'black' : 'white')
        }

        window.resetDefaultMessages = () => {
            window.showMessage('')
            window.setGenerateMessage('')
        }

        window.hideSelectedNode = () => {
            setSelectedNodeId(cur => {
                if (cur !== null) {
                    s.hiddenNodes.add(cur)
                    removeSatellitesForNode(cur)
                    n.body.data.nodes.update({ id: cur, hidden: true })
                    setHasHidden(true)
                }
                return null
            })
        }

        window.showAllNodes = () => {
            s.hiddenNodes.forEach(id => n.body.data.nodes.update({ id, hidden: false }))
            s.hiddenNodes.clear()
            setHasHidden(false)
        }

        window.setLazyMode = (enabled) => {
            s.isLazyMode = enabled
            setIsLazyMode(enabled)
            window.__levaSet?.({ 'Lazy Mode': enabled })
        }

        window.setInitialSettings = (settings) => {
            s.isLazyMode = settings.lazyExpansion
            setIsLazyMode(settings.lazyExpansion)
            ideBackgroundColor.current = settings.ideBackgroundColor || '#000000'
            customBackgroundColor.current = settings.customBackgroundColor || '#000000'
            const useIde = settings.backgroundType === 'ide'
            const bg = useIde ? settings.ideBackgroundColor : (settings.customBackgroundColor || '#000000')
            window.__levaSet?.({
                'Lazy Mode': settings.lazyExpansion,
                'Max Depth': Math.max(2, settings.maxDepth),
                'Direction': settings.graphDirection,
                'Filter Out Tests': settings.filterTestCode,
                'Use IDE Background': useIde,
                'Background Color': settings.customBackgroundColor || '#000000',
            })
            applyBackground(bg)
        }

        window.fit = () => {
            n.fit()
            setIsGraphFitted(true)
        }

        window.reposition = () => {
            removeAllSatellites()
            // Unpin all non-root nodes so physics can actually move them
            n.body.data.nodes.getIds().forEach(id => {
                if (id === s.rootNodeId || typeof id === 'string') return
                n.body.data.nodes.update({ id, fixed: { x: false, y: false } })
            })

            window.__setRepositionProgress?.(0)

            const onProgress = (params) => {
                window.__setRepositionProgress?.(Math.round(params.iterations / params.total * 100))
            }
            n.on('stabilizationProgress', onProgress)

            n.setOptions({ physics: { enabled: true } })
            n.once('stabilizationIterationsDone', () => {
                n.off('stabilizationProgress', onProgress)
                window.__setRepositionProgress?.(null)
                // Add satellites while physics is still on so they settle into position
                n.body.data.nodes.getIds().forEach(id => {
                    if (typeof id !== 'string') showSatellitesForNode(id)
                })
                n.once('stabilizationIterationsDone', () => {
                    n.setOptions({ physics: { enabled: false } })
                    n.fit()
                    setIsGraphFitted(true)
                })
                n.stabilize()
            })
            n.stabilize()
        }

        window.resetDefaultMessages()

        return () => n.destroy()
    }, []) // eslint-disable-line react-hooks/exhaustive-deps

    // ── Helpers (used in JSX and window.reposition) ───────────────────────────

    return (
        <>
            <Leva
                theme={{
                    colors: {
                        elevation1: '#3a3a3a',
                        elevation2: '#252525',
                        elevation3: '#2f2f2f',
                        accent1: '#4A7FC1',
                        accent2: '#5A8FD1',
                        accent3: '#6A9FE1',
                        highlight1: '#aaa',
                        highlight2: '#ccc',
                        highlight3: '#eee',
                        vivid1: '#5BAD6F',
                        folderWidgetColor: '#999',
                        folderTextColor: '#CCC',
                        toolTipBackground: '#3a3a3a',
                        toolTipText: '#eee',
                    },
                    fontSizes: { root: '12px' },
                    sizes: { rootWidth: '280px', controlWidth: '120px', rowHeight: '26px' },
                    radii: { xs: '2px', sm: '2px', lg: '2px' },
                    space: { xs: '4px', sm: '6px', md: '8px', lg: '10px' },
                    shadows: { level1: 'none', level2: '0 2px 12px rgba(0,0,0,0.5)', level3: '0 2px 8px rgba(0,0,0,0.8)' },
                }}
                titleBar={{ title: 'Graph Settings', drag: true, filter: false }}
                hideCopyButton
            />

            <div className="centered-message" style={{ color: messageColor, display: showNetwork ? 'none' : 'block' }}>
                {message}
            </div>

            <div id="network" ref={containerRef} style={{ display: showNetwork ? 'block' : 'none' }} />

            {repositionProgress !== null && (
                <div className="reposition-overlay">
                    <div className="reposition-progress-box">
                        <div className="reposition-label">REPOSITIONING</div>
                        <div className="reposition-track">
                            <div className="reposition-fill" style={{ width: repositionProgress + '%' }} />
                        </div>
                        <div className="reposition-pct">{repositionProgress}%</div>
                    </div>
                </div>
            )}

            <div className="navbar">
                {isGraphGenerated && needsRegen && (
                    <div className="regen-warning" onClick={() => window.JavaBridge.generateGraph()}>
                        <i className="fas fa-exclamation-triangle" style={{ marginRight: 6 }} />
                        GRAPH MUST BE REGENERATED
                    </div>
                )}
                <div className="generate-row" onClick={() => window.JavaBridge.generateGraph()} style={{ cursor: 'pointer' }}>
                    <button className="navbutton"><i className="fas fa-magic button-icon" />GENERATE</button>
                    {genMsg.text && <div className={`gen-message ${genMsg.cls}`}>{genMsg.text}</div>}
                </div>

                {isGraphGenerated && !isGraphFitted && (
                    <button className="navbutton" onClick={() => window.fit()}><i className="fas fa-expand-arrows-alt button-icon" />FIT</button>
                )}

                {isGraphGenerated && (
                    <button className="navbutton" onClick={() => window.reposition()}><i className="fas fa-project-diagram button-icon" />REPOSITION</button>
                )}

                {selectedNodeId !== null && (
                    <button className="navbutton" onClick={() => window.hideSelectedNode()}><i className="fas fa-eye-slash button-icon" />HIDE NODE</button>
                )}

                {hasHidden && (
                    <button className="navbutton" onClick={() => window.showAllNodes()}><i className="fas fa-eye button-icon" />SHOW ALL</button>
                )}

                {isGraphGenerated && (
                    <button className="navbutton" onClick={() => window.JavaBridge.saveAsHtml()}><i className="fas fa-download button-icon" />SAVE AS HTML</button>
                )}
            </div>

            <LevaTooltip />
        </>
    )
}

const LEVA_HINTS = {
    'Lazy Mode': 'Start with just the selected method and expand callers/callees one step at a time by clicking the + nodes. When off, the full graph is generated up to Max Depth.',
    'Max Depth': 'How many levels deep to traverse when Lazy Mode is off. Higher values can produce very large graphs.',
    'Direction': 'Callers: who calls the selected method. Callees: what the selected method calls. Both: expand in both directions.',
    'Filter Out Tests': 'Hide test classes and methods from the graph. Detects JUnit/TestNG annotations, class names starting with Test, and files in test source roots.',
    'Physics': 'Enable live physics simulation. Nodes will repel and settle naturally. Useful after adding many nodes — or use the Reposition button instead.',
    'Use IDE Background': 'Use the IDE editor background color instead of a custom color.',
    'Background Color': 'Custom background color for the graph canvas.',
}

function LevaTooltip() {
    const [tooltip, setTooltip] = useState(null)

    useEffect(() => {
        function onMouseOver(e) {
            let el = e.target
            while (el && el !== document.body) {
                const text = el.textContent?.trim()
                if (text && LEVA_HINTS[text]) {
                    setTooltip({ text: LEVA_HINTS[text], x: e.clientX, y: e.clientY })
                    return
                }
                el = el.parentElement
            }
            setTooltip(null)
        }
        function onMouseMove(e) {
            setTooltip(cur => cur ? { ...cur, x: e.clientX, y: e.clientY } : null)
        }
        document.addEventListener('mouseover', onMouseOver)
        document.addEventListener('mousemove', onMouseMove)
        return () => {
            document.removeEventListener('mouseover', onMouseOver)
            document.removeEventListener('mousemove', onMouseMove)
        }
    }, [])

    if (!tooltip) return null
    return (
        <div style={{
            position: 'fixed', left: tooltip.x + 14, top: tooltip.y + 14,
            background: '#1e1e1e', border: '1px solid #444', color: '#ddd',
            fontSize: '11px', padding: '6px 9px', borderRadius: '3px',
            maxWidth: '230px', zIndex: 99999, pointerEvents: 'none', lineHeight: '1.5',
            boxShadow: '0 2px 8px rgba(0,0,0,0.7)',
        }}>
            {tooltip.text}
        </div>
    )
}

function applyBackground(color) {
    document.documentElement.style.backgroundColor = color
    document.body.style.backgroundColor = color
    const root = document.getElementById('root')
    if (root) root.style.backgroundColor = color
    const el = document.getElementById('network')
    if (el) el.style.backgroundColor = color
}

function seedPositionNearParent(nodes, parentId) {
    if (!parentId || !nodes.length) return nodes
    const n = window.__networkInstance
    if (!n) return nodes
    const parentPos = n.getPositions([parentId])[parentId]
    if (!parentPos) return nodes
    return nodes.map(node => {
        const angle = Math.random() * 2 * Math.PI
        const r = 40 + Math.random() * 40
        return Object.assign({}, node, { x: parentPos.x + Math.cos(angle) * r, y: parentPos.y + Math.sin(angle) * r })
    })
}
