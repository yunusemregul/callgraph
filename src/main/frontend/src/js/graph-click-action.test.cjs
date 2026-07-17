const test = require('node:test')
const assert = require('node:assert/strict')
const { getGraphClickAction } = require('./graph-click-action.cjs')

test('node navigation wins when vis-network also selects its connected edge', () => {
    assert.deepEqual(
        getGraphClickAction({ nodes: [12], edges: [37] }),
        { type: 'node', id: 12 },
    )
})

test('an edge navigates to its call site when no node is selected', () => {
    assert.deepEqual(
        getGraphClickAction({ nodes: [], edges: [37] }),
        { type: 'edge', id: 37 },
    )
})

test('satellite nodes expand instead of navigating', () => {
    assert.deepEqual(
        getGraphClickAction({ nodes: ['__callees__12'], edges: ['__edge_callees__12'] }),
        { type: 'satellite', id: '__callees__12' },
    )
})

test('satellite edges never navigate to source', () => {
    assert.deepEqual(
        getGraphClickAction({ nodes: [], edges: ['__edge_callers__12'] }),
        { type: 'none' },
    )
})
