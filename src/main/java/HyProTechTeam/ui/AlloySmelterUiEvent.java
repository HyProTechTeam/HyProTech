package HyProTechTeam.ui;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.simple.IntegerCodec;
import com.hypixel.hytale.codec.codecs.simple.StringCodec;

/** Carries alloy smelter UI actions and item drag or slot data. */
public class AlloySmelterUiEvent {
    // Map named UI payload fields to their event properties.
    public static final BuilderCodec<AlloySmelterUiEvent> CODEC =
            BuilderCodec.<AlloySmelterUiEvent>builder(AlloySmelterUiEvent.class, AlloySmelterUiEvent::new)
                    .addField(new KeyedCodec<>("Action", new StringCodec()),
                            (event, value) -> event.action = value,
                            event -> event.action)
                    .addField(new KeyedCodec<>("DragItemStackId", new StringCodec()),
                            (event, value) -> event.dragItemStackId = value,
                            event -> event.dragItemStackId)
                    .addField(new KeyedCodec<>("DragItemStackQuantity", new IntegerCodec()),
                            (event, value) -> event.dragItemStackQuantity = value,
                            event -> event.dragItemStackQuantity)
                    .addField(new KeyedCodec<>("DragSourceInventorySectionId", new StringCodec()),
                            (event, value) -> event.dragSourceInventorySectionId = value,
                            event -> event.dragSourceInventorySectionId)
                    .addField(new KeyedCodec<>("DragSourceItemGridIndex", new IntegerCodec()),
                            (event, value) -> event.dragSourceItemGridIndex = value,
                            event -> event.dragSourceItemGridIndex)
                    .addField(new KeyedCodec<>("DragSourceSlotId", new IntegerCodec()),
                            (event, value) -> event.dragSourceSlotId = value,
                            event -> event.dragSourceSlotId)
                    .addField(new KeyedCodec<>("SlotIndex", new IntegerCodec()),
                            (event, value) -> event.slotIndex = value,
                            event -> event.slotIndex)
                    .addField(new KeyedCodec<>("SourceSlotId", new IntegerCodec()),
                            (event, value) -> event.sourceSlotId = value,
                            event -> event.sourceSlotId)
                    .addField(new KeyedCodec<>("SourceItemGridIndex", new IntegerCodec()),
                            (event, value) -> event.sourceItemGridIndex = value,
                            event -> event.sourceItemGridIndex)
                    .addField(new KeyedCodec<>("ItemStackId", new StringCodec()),
                            (event, value) -> event.itemStackId = value,
                            event -> event.itemStackId)
                    .addField(new KeyedCodec<>("ItemStackQuantity", new IntegerCodec()),
                            (event, value) -> event.itemStackQuantity = value,
                            event -> event.itemStackQuantity)
                    .addField(new KeyedCodec<>("PressedMouseButton", new IntegerCodec()),
                            (event, value) -> event.pressedMouseButton = value,
                            event -> event.pressedMouseButton)
                    .addField(new KeyedCodec<>("SourceInventorySectionId", new StringCodec()),
                            (event, value) -> event.sourceInventorySectionId = value,
                            event -> event.sourceInventorySectionId)
                    .addField(new KeyedCodec<>("Target", new StringCodec()),
                            (event, value) -> event.target = value,
                            event -> event.target)
                    .build();

    private String action;
    private String dragItemStackId;
    private Integer dragItemStackQuantity;
    private String dragSourceInventorySectionId;
    private Integer dragSourceItemGridIndex;
    private Integer dragSourceSlotId;
    private Integer slotIndex;
    private Integer sourceSlotId;
    private Integer sourceItemGridIndex;
    private String itemStackId;
    private Integer itemStackQuantity;
    private Integer pressedMouseButton;
    private String sourceInventorySectionId;
    private String target;

    /** Returns the UI action. */
    public String getAction() {
        return action;
    }

    /** Sets the UI action. */
    public void setAction(String action) {
        this.action = action;
    }

    /** Returns the dragged item ID. */
    public String getDragItemStackId() {
        return dragItemStackId;
    }

    /** Sets the dragged item ID. */
    public void setDragItemStackId(String dragItemStackId) {
        this.dragItemStackId = dragItemStackId;
    }

    /** Returns the dragged item quantity. */
    public Integer getDragItemStackQuantity() {
        return dragItemStackQuantity;
    }

    /** Sets the dragged item quantity. */
    public void setDragItemStackQuantity(Integer dragItemStackQuantity) {
        this.dragItemStackQuantity = dragItemStackQuantity;
    }

    /** Returns the dragged stack's source inventory section. */
    public String getDragSourceInventorySectionId() {
        return dragSourceInventorySectionId;
    }

    /** Sets the dragged stack's source inventory section. */
    public void setDragSourceInventorySectionId(String dragSourceInventorySectionId) {
        this.dragSourceInventorySectionId = dragSourceInventorySectionId;
    }

    /** Returns the dragged stack's source grid index. */
    public Integer getDragSourceItemGridIndex() {
        return dragSourceItemGridIndex;
    }

    /** Sets the dragged stack's source grid index. */
    public void setDragSourceItemGridIndex(Integer dragSourceItemGridIndex) {
        this.dragSourceItemGridIndex = dragSourceItemGridIndex;
    }

    /** Returns the dragged stack's source slot ID. */
    public Integer getDragSourceSlotId() {
        return dragSourceSlotId;
    }

    /** Sets the dragged stack's source slot ID. */
    public void setDragSourceSlotId(Integer dragSourceSlotId) {
        this.dragSourceSlotId = dragSourceSlotId;
    }

    /** Returns the slot index. */
    public Integer getSlotIndex() {
        return slotIndex;
    }

    /** Sets the slot index. */
    public void setSlotIndex(Integer slotIndex) {
        this.slotIndex = slotIndex;
    }

    /** Returns the source slot ID. */
    public Integer getSourceSlotId() {
        return sourceSlotId;
    }

    /** Sets the source slot ID. */
    public void setSourceSlotId(Integer sourceSlotId) {
        this.sourceSlotId = sourceSlotId;
    }

    /** Returns the source grid index. */
    public Integer getSourceItemGridIndex() {
        return sourceItemGridIndex;
    }

    /** Sets the source grid index. */
    public void setSourceItemGridIndex(Integer sourceItemGridIndex) {
        this.sourceItemGridIndex = sourceItemGridIndex;
    }

    /** Returns the item stack ID. */
    public String getItemStackId() {
        return itemStackId;
    }

    /** Sets the item stack ID. */
    public void setItemStackId(String itemStackId) {
        this.itemStackId = itemStackId;
    }

    /** Returns the item stack quantity. */
    public Integer getItemStackQuantity() {
        return itemStackQuantity;
    }

    /** Sets the item stack quantity. */
    public void setItemStackQuantity(Integer itemStackQuantity) {
        this.itemStackQuantity = itemStackQuantity;
    }

    /** Returns the pressed mouse button. */
    public Integer getPressedMouseButton() {
        return pressedMouseButton;
    }

    /** Sets the pressed mouse button. */
    public void setPressedMouseButton(Integer pressedMouseButton) {
        this.pressedMouseButton = pressedMouseButton;
    }

    /** Returns the source inventory section. */
    public String getSourceInventorySectionId() {
        return sourceInventorySectionId;
    }

    /** Sets the source inventory section. */
    public void setSourceInventorySectionId(String sourceInventorySectionId) {
        this.sourceInventorySectionId = sourceInventorySectionId;
    }

    /** Returns the event target. */
    public String getTarget() {
        return target;
    }

    /** Sets the event target. */
    public void setTarget(String target) {
        this.target = target;
    }
}
