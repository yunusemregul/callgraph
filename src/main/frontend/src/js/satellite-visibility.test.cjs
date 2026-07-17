const test = require('node:test')
const assert = require('node:assert/strict')
const { getSatelliteVisibility } = require('./satellite-visibility.cjs')

test('lazy root exposes each available direction', () => {
    assert.deepEqual(getSatelliteVisibility({
        isLazyMode: true,
        isRoot: true,
        hasCallers: true,
        hasCallees: true,
    }), { showCallers: true, showCallees: true })
})

test('lazy callee can recursively expose its callees', () => {
    assert.equal(getSatelliteVisibility({
        isLazyMode: true,
        hasCallees: true,
    }).showCallees, true)
})

test('unavailable and expanded directions remain hidden', () => {
    assert.deepEqual(getSatelliteVisibility({
        isLazyMode: true,
        callersExpanded: true,
        hasCallers: true,
        hasCallees: false,
    }), { showCallers: false, showCallees: false })
})

test('truncated caller results remain expandable outside lazy mode', () => {
    assert.equal(getSatelliteVisibility({
        isTruncated: true,
        hasCallers: true,
    }).showCallers, true)
})

test('only the root can expose callees outside lazy mode', () => {
    assert.equal(getSatelliteVisibility({
        isRoot: true,
        hasCallees: true,
    }).showCallees, true)

    assert.equal(getSatelliteVisibility({
        hasCallees: true,
    }).showCallees, false)
})

test('manual expansion remains recursive outside lazy mode', () => {
    assert.equal(getSatelliteVisibility({
        manuallyExpandableCallees: true,
        hasCallees: true,
    }).showCallees, true)

    assert.equal(getSatelliteVisibility({
        manuallyExpandableCallers: true,
        hasCallers: true,
    }).showCallers, true)
})
