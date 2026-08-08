package me.alexisbinh.openlootr.container;

import java.util.Objects;

public sealed interface ContainerResolution {
    record Ignored(String reason) implements ContainerResolution {
        public Ignored { Objects.requireNonNull(reason, "reason"); }
    }

    record Candidate(ContainerDescriptor descriptor) implements ContainerResolution {
        public Candidate { Objects.requireNonNull(descriptor, "descriptor"); }
    }

    record Managed(ContainerDescriptor descriptor) implements ContainerResolution {
        public Managed { Objects.requireNonNull(descriptor, "descriptor"); }
    }

    record Broken(String reason) implements ContainerResolution {
        public Broken { Objects.requireNonNull(reason, "reason"); }
    }
}
