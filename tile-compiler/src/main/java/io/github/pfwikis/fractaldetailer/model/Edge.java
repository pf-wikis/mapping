package io.github.pfwikis.fractaldetailer.model;

public record Edge(LngLat a, LngLat b) {

    public Edge norm() {
        return new Edge(a.norm(), b.norm());
    }

}
