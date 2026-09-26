package com.yunpu.yunpumusic.api;

/** A verified account membership; UNKNOWN means the provider could not confirm it. */
public enum Membership {
    UNKNOWN("未验证"), FREE("普通"), VIP("VIP"), SVIP("SVIP");

    private final String label;
    Membership(String label) { this.label = label; }
    public String label() { return label; }
}
