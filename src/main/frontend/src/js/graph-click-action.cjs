function getGraphClickAction(params) {
    const nodes = params?.nodes || []
    const edges = params?.edges || []

    if (nodes.length === 1) {
        const id = nodes[0]
        if (typeof id === 'string' && id.startsWith('__')) {
            return { type: 'satellite', id }
        }
        return { type: 'node', id }
    }

    if (edges.length === 1) {
        const id = edges[0]
        if (typeof id !== 'string' || !id.startsWith('__')) {
            return { type: 'edge', id }
        }
    }

    return { type: 'none' }
}

module.exports = { getGraphClickAction }
