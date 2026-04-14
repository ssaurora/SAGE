package com.sage.backend.scene.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeveloperTraceSupportDataDTO {

    @JsonProperty("demo_live_simulation")
    private DemoLiveSimulationSupportDTO demoLiveSimulation;

    public DemoLiveSimulationSupportDTO getDemoLiveSimulation() {
        return demoLiveSimulation;
    }

    public void setDemoLiveSimulation(DemoLiveSimulationSupportDTO demoLiveSimulation) {
        this.demoLiveSimulation = demoLiveSimulation;
    }
}
