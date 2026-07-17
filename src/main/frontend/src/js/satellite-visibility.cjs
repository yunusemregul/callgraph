function getSatelliteVisibility({
    isLazyMode = false,
    isRoot = false,
    isTruncated = false,
    manuallyExpandableCallers = false,
    manuallyExpandableCallees = false,
    callersExpanded = false,
    calleesExpanded = false,
    hasCallers = false,
    hasCallees = false,
}) {
    return {
        showCallers: Boolean(hasCallers) && !callersExpanded
            && (isLazyMode || isTruncated || manuallyExpandableCallers),
        showCallees: Boolean(hasCallees) && !calleesExpanded
            && (isLazyMode || isRoot || manuallyExpandableCallees),
    }
}

module.exports = { getSatelliteVisibility }
