package com.undercover.vpn.bg;

import com.undercover.vpn.bg.ParceledListSlice;

interface INeighborTableCallback {
    oneway void onNeighborTableUpdated(in ParceledListSlice entries);
}
