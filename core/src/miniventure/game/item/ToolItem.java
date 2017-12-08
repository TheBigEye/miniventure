package miniventure.game.item;

public class ToolItem extends Item {
	
	private final ToolType toolType;
	
	public ToolItem(ToolType type) {
		super(type.texture);
		this.toolType = type;
	}
	
	public ToolType getType() { return toolType; }
	
	
	@Override
	public boolean isReflexive() { return false; }
}