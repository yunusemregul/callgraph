import { createRoot } from 'react-dom/client'
import { useControls, useStoreContext, Leva } from 'leva'
import { useEffect, useRef } from 'react'

function Controls({ onReady }) {
    const initialized = useRef(false)

    const [values, set] = useControls(() => ({
        'Lazy Mode': {
            value: true,
            onChange: (v, _, { initial }) => {
                if (initial) return
                window.__levaLazyMode(v)
                window.JavaBridge.updateSetting('lazyExpansion|' + v)
            }
        },
        'Max Depth': {
            value: 5,
            min: 1,
            max: 50,
            step: 1,
            onChange: (v, _, { initial }) => {
                if (initial) return
                window.JavaBridge.updateSetting('maxDepth|' + Math.round(v))
            }
        },
        'Direction': {
            value: 'callers',
            options: { Callers: 'callers', Callees: 'callees', Both: 'both' },
            onChange: (v, _, { initial }) => {
                if (initial) return
                window.JavaBridge.updateSetting('graphDirection|' + v)
            }
        },
        'Filter Tests': {
            value: false,
            onChange: (v, _, { initial }) => {
                if (initial) return
                window.JavaBridge.updateSetting('filterTestCode|' + v)
            }
        },
        'Physics': {
            value: false,
            onChange: (v, _, { initial }) => {
                if (initial) return
                window.__levaPhysics(v)
            }
        },
    }))

    useEffect(() => {
        // Expose setter so Java can push initial values
        window.__levaSet = set
        onReady()
    }, [])

    return null
}

export function mountSettingsPanel(containerId) {
    const container = document.getElementById(containerId)
    if (!container) return

    const root = createRoot(container)
    root.render(
        <>
            <Leva
                fill
                flat
                titleBar={false}
                theme={{
                    colors: {
                        elevation1: '#1e1e1e',
                        elevation2: '#2a2a2a',
                        elevation3: '#333',
                        accent1: '#4A7FC1',
                        accent2: '#5A8FD1',
                        accent3: '#6A9FE1',
                        highlight1: '#555',
                        highlight2: '#666',
                        highlight3: '#777',
                        vivid1: '#5BAD6F',
                        folderWidgetColor: '#888',
                        folderTextColor: '#CCC',
                        toolTipBackground: '#111',
                        toolTipText: '#DDD',
                    },
                    fontSizes: {
                        root: '11px',
                    },
                    sizes: {
                        rootWidth: '200px',
                        controlWidth: '90px',
                        rowHeight: '24px',
                    },
                    radii: { xs: '2px', sm: '2px', lg: '2px' },
                    space: { xs: '4px', sm: '6px', md: '8px', lg: '10px' },
                }}
            />
            <Controls onReady={() => {}} />
        </>
    )
}
