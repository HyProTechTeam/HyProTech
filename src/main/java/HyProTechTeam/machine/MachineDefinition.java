package HyProTechTeam.machine;

import HyProTechTeam.energy.EnergyNodeComponent;
import HyProTechTeam.energy.EnergySide;

/** Describes how a machine is recognized, initialized, configured, and ticked. */
public interface MachineDefinition {
    /** Returns the identifier stored on machine components. */
    String id();

    /** Reports whether this machine definition applies to a block ID. */
    boolean matchesBlockId(String blockId);

    /** Creates persistent machine state carrying this definition's ID. */
    default MachineComponent createMachineComponent() {
        MachineComponent component = new MachineComponent();
        component.setMachineId(id());
        return component;
    }

    /** Creates a machine energy node with every side enabled initially. */
    default EnergyNodeComponent createEnergyNode() {
        EnergyNodeComponent node = new EnergyNodeComponent();
        node.setNodeType(EnergyNodeComponent.NodeType.MACHINE);
        node.setInputMask(EnergySide.ALL_MASK);
        node.setOutputMask(EnergySide.ALL_MASK);
        return node;
    }

    /** Allows a definition to reconcile machine and energy defaults before each tick. */
    default void configureDefaults(MachineComponent machine, EnergyNodeComponent energy) {
    }

    /**
     * @return true if the machine component changed and should be saved.
     */
    boolean tick(MachineContext context, float deltaSeconds);
}
