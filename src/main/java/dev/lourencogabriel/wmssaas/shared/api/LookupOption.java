package dev.lourencogabriel.wmssaas.shared.api;

/** Only the identifier and human-readable label needed to populate a dropdown. */
public record LookupOption(String id, String label) {}
