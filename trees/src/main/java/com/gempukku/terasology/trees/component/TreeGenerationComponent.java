package com.gempukku.terasology.trees.component;

import com.gempukku.secsy.entity.Component;
import com.gempukku.terasology.communication.SharedComponent;

@SharedComponent
public interface TreeGenerationComponent extends Component {
    String getGenerationType();
}
