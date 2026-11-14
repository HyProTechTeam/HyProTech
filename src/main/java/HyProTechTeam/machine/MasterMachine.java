package HyProTechTeam.machine;

import HyProTechTeam.energy.EnergyNodeComponent;
import HyProTechTeam.energy.EnergySide;

/** Base definition that supplies generic machine energy and progress defaults. */
public abstract class MasterMachine implements MachineDefinition {
    /** Returns the fallback energy capacity. */
    protected int defaultCapacity() {
        return 50000;
    }

    /** Returns the fallback maximum energy transfer. */
    protected int defaultMaxTransfer() {
        return 5000;
    }

    /** Returns the fallback progress threshold. */
    protected int defaultProgressMax() {
        return 100;
    }

    /** Fills missing values and restores all side masks when both masks are empty. */
    @Override
    public void configureDefaults(MachineComponent machine, EnergyNodeComponent energy) {
        if (energy != null) {
            if (energy.getNodeType() != EnergyNodeComponent.NodeType.MACHINE) {
                energy.setNodeType(EnergyNodeComponent.NodeType.MACHINE);
            }
            if (energy.getCapacity() <= 0) {
                energy.setCapacity(defaultCapacity());
            }
            if (energy.getMaxTransfer() <= 0) {
                energy.setMaxTransfer(defaultMaxTransfer());
            }
            // An entirely empty pair of masks is replaced with the default open sides.
            if (energy.getInputMask() == 0 && energy.getOutputMask() == 0) {
                energy.setInputMask(EnergySide.ALL_MASK);
                energy.setOutputMask(EnergySide.ALL_MASK);
            }
        }
        if (machine != null && machine.getProgressMax() <= 0) {
            machine.setProgressMax(defaultProgressMax());
        }
    }
}
