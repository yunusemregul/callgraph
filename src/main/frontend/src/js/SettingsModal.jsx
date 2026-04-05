import { useState, useEffect } from 'react'

const styles = {
    overlay: {
        position: 'fixed', inset: 0,
        background: 'rgba(0,0,0,0.6)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        zIndex: 9999,
        backdropFilter: 'blur(2px)',
    },
    modal: {
        background: '#1a1a1a',
        border: '1px solid #333',
        borderRadius: '4px',
        width: '320px',
        boxShadow: '0 8px 32px rgba(0,0,0,0.6)',
        fontFamily: "'SFMono-Regular', Menlo, Consolas, monospace",
        overflow: 'hidden',
    },
    header: {
        background: '#252525',
        borderBottom: '1px solid #333',
        padding: '10px 14px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
    },
    title: {
        color: '#ccc',
        fontSize: '12px',
        letterSpacing: '2px',
        textTransform: 'uppercase',
        margin: 0,
    },
    closeBtn: {
        background: 'none',
        border: 'none',
        color: '#666',
        cursor: 'pointer',
        fontSize: '1.1em',
        padding: '0 2px',
        lineHeight: 1,
        transition: 'color 0.15s',
    },
    body: {
        padding: '14px',
    },
    sectionTitle: {
        color: '#666',
        fontSize: '12px',
        letterSpacing: '2px',
        textTransform: 'uppercase',
        marginBottom: '10px',
        paddingBottom: '6px',
        borderBottom: '1px solid #2f2f2f',
    },
    row: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '6px 0',
    },
    label: {
        color: '#aaa',
        fontSize: '12px',
        letterSpacing: '1px',
        textTransform: 'uppercase',
        cursor: 'pointer',
        userSelect: 'none',
    },
    labelDisabled: {
        color: '#555',
    },
    toggle: { position: 'relative', width: '34px', height: '18px', flexShrink: 0 },
    toggleInput: { opacity: 0, width: 0, height: 0, position: 'absolute' },
    colorRow: {
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
        padding: '6px 0',
    },
    colorInput: {
        width: '32px',
        height: '22px',
        padding: '0',
        border: '1px solid #444',
        borderRadius: '2px',
        cursor: 'pointer',
        background: 'none',
    },
    colorValue: {
        color: '#888',
        fontSize: '0.68em',
        letterSpacing: '1px',
        fontFamily: 'monospace',
    },
}

function Toggle({ checked, onChange, disabled }) {
    return (
        <label style={styles.toggle}>
            <input type="checkbox" style={styles.toggleInput} checked={checked} onChange={e => onChange(e.target.checked)} disabled={disabled} />
            <span style={{
                position: 'absolute', cursor: disabled ? 'default' : 'pointer',
                inset: 0, background: checked && !disabled ? '#4A7FC1' : '#333',
                borderRadius: '18px', transition: '0.2s',
                border: '1px solid ' + (checked && !disabled ? '#2B5FA0' : '#444'),
            }}>
                <span style={{
                    position: 'absolute', width: '12px', height: '12px',
                    left: checked ? '17px' : '3px', top: '2px',
                    background: checked && !disabled ? '#fff' : '#666',
                    borderRadius: '50%', transition: '0.2s',
                }} />
            </span>
        </label>
    )
}

export default function SettingsModal({ onClose, initialSettings }) {
    const [useIde, setUseIde] = useState(initialSettings.backgroundType === 'ide')
    const [customColor, setCustomColor] = useState(initialSettings.customBackgroundColor || '#000000')

    const effectiveColor = useIde ? initialSettings.ideBackgroundColor : customColor

    useEffect(() => {
        applyBackground(effectiveColor)
    }, [effectiveColor])

    function applyBackground(color) {
        document.body.style.backgroundColor = color
        const net = document.getElementById('network')
        if (net) net.style.backgroundColor = color
    }

    function handleUseIdeToggle(val) {
        setUseIde(val)
        const type = val ? 'ide' : 'custom'
        window.JavaBridge.updateSetting('backgroundType|' + type)
    }

    function handleColorChange(val) {
        setCustomColor(val)
        if (!useIde) {
            window.JavaBridge.updateSetting('customBackgroundColor|' + val)
        }
    }

    return (
        <div style={styles.overlay} onClick={e => { if (e.target === e.currentTarget) onClose() }}>
            <div style={styles.modal}>
                <div style={styles.header}>
                    <span style={styles.title}>Options</span>
                    <button style={styles.closeBtn} onClick={onClose}
                        onMouseEnter={e => e.target.style.color = '#ccc'}
                        onMouseLeave={e => e.target.style.color = '#666'}>✕</button>
                </div>
                <div style={styles.body}>
                    <div style={styles.sectionTitle}>Appearance</div>

                    <div style={styles.row}>
                        <label style={styles.label} onClick={() => handleUseIdeToggle(!useIde)}>
                            Use IDE Background
                        </label>
                        <Toggle checked={useIde} onChange={handleUseIdeToggle} />
                    </div>

                    <div style={styles.colorRow}>
                        <label style={{ ...styles.label, ...(useIde ? styles.labelDisabled : {}) }}>
                            Custom Color
                        </label>
                        <input
                            type="color"
                            style={{ ...styles.colorInput, opacity: useIde ? 0.3 : 1 }}
                            value={customColor}
                            disabled={useIde}
                            onChange={e => handleColorChange(e.target.value)}
                        />
                        <span style={{ ...styles.colorValue, opacity: useIde ? 0.4 : 1 }}>{customColor}</span>
                    </div>
                </div>
            </div>
        </div>
    )
}
