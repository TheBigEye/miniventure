package miniventure.game.item;

import java.util.ArrayList;
import java.util.HashMap;

import miniventure.game.GameCore;
import miniventure.game.GameProtocol.RecipeRequest;
import miniventure.game.GameProtocol.RecipeStockUpdate;
import miniventure.game.GameProtocol.SerialRecipe;
import miniventure.game.client.ClientCore;
import miniventure.game.screen.MenuScreen;
import miniventure.game.screen.util.ColorBackground;
import miniventure.game.util.RelPos;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;

import org.jetbrains.annotations.NotNull;

/** @noinspection SynchronizeOnThis*/
public class CraftingScreen extends MenuScreen {
	
	// private static final Color slotBackground = Color.BLUE.cpy().lerp(Color.WHITE, .1f);
	private static final Color tableBackground = Color.SKY.cpy().lerp(Color.BLACK, .2f);
	private static final Color craftableBackground = Color.GREEN.cpy().lerp(tableBackground, .5f);
	private static final Color notCraftableBackground = Color.FIREBRICK.cpy().lerp(tableBackground, .3f);
	
	/*
		general system:
		
		- client normally has no reference to inventory at all, only hotbar
			- server sends hotbar updates as necessary
		- when inventory screen opened, client requests inventory data
			- server sends back InventoryUpdate with inventory data and hotbar indices
		
	 */
	
	private boolean requested = false;
	
	private ArrayList<RecipeSlot> recipes = null;
	private final HashMap<String, Integer> inventoryCounts = new HashMap<>();
	
	private final Table mainGroup;
	
	private final Table craftableTable;
	private final Table costTable;
	private Label resultLabel;
	private final HashMap<String, Label> costLabels = new HashMap<>();
	
	private final ScrollPane scrollPane;
	private final Table recipeListTable;
	
	private int selection;
	
	public CraftingScreen() {
		super(false);
		
		mainGroup = useTable(Align.topLeft, false);
		addMainGroup(mainGroup, RelPos.TOP_LEFT);
		
		mainGroup.add(makeLabel("personal crafting", 12)).colspan(2).row();
		
		craftableTable = new Table(GameCore.getSkin());
		craftableTable.defaults().pad(2f);
		craftableTable.pad(10f);
		craftableTable.add(makeLabel("Waiting for crafting data...", false));
		
		costTable = new Table(GameCore.getSkin());
		costTable.pad(5f);
		costTable.defaults().pad(2f);
		costTable.add(makeLabel("Waiting for crafting data...", false));
		
		recipeListTable = new Table(GameCore.getSkin()) {
			@Override
			protected void drawChildren(Batch batch, float parentAlpha) {
				boolean done = false;
				synchronized (CraftingScreen.this) {
					if(recipes != null) {
						done = true;
						if(recipes.size() > 0)
							recipes.get(selection).setSelected(true);
						super.drawChildren(batch, parentAlpha);
						if(recipes.size() > 0) recipes.get(selection).setSelected(false);
					}
				}
				if(!done)
					super.drawChildren(batch, parentAlpha);
			}
		};
		recipeListTable.defaults().pad(1f).fillX().minSize(Item.ICON_SIZE * 3, ItemSlot.HEIGHT/2);
		recipeListTable.pad(10f);
		recipeListTable.add(makeLabel("Waiting for crafting data...", false));
		
		recipeListTable.addListener(new InputListener() {
			@Override
			public boolean keyDown(InputEvent event, int keycode) {
				if(keycode == Keys.Z || keycode == Keys.ESCAPE) {
					ClientCore.setScreen(null);
					return true;
				}
				
				synchronized (CraftingScreen.this) {
					if(recipes.size() > 0) {
						if(keycode == Keys.ENTER) {
							// TODO send craft request
							return true;
						}
					}
				}
				
				return false;
			}
		});
		
		scrollPane = new ScrollPane(recipeListTable, GameCore.getSkin()) {
			@Override
			public float getPrefHeight() {
				return CraftingScreen.this.getHeight()/2;
			}
		};
		
		// scrollPane.setHeight(getHeight()*2/3);
		scrollPane.setScrollingDisabled(true, false);
		scrollPane.setFadeScrollBars(false);
		scrollPane.setScrollbarsOnTop(false);
		
		Table leftTable = new Table();
		leftTable.pad(0);
		leftTable.defaults().pad(0);
		
		leftTable.add(craftableTable).row();
		leftTable.add(scrollPane).row();
		
		mainGroup.add(leftTable);
		mainGroup.add(costTable).align(Align.top);
		
		mainGroup.pack();
		mainGroup.background(new ColorBackground(mainGroup, tableBackground));
		
		setKeyboardFocus(recipeListTable);
		setScrollFocus(scrollPane);
	}
	
	// should only be called by the LibGDX Application thread
	@Override
	public synchronized void focus() {
		super.focus();
		if(!requested) {
			requested = true;
			ClientCore.getClient().send(new RecipeRequest());
		}
	}
	
	// should only be called by GameClient thread.
	public void recipeUpdate(RecipeRequest recipeRequest) {
		if(ClientCore.getScreen() != this)
			return; // don't care
		
		synchronized (this) {
			recipes = new ArrayList<>(recipeRequest.recipes.length);
			recipeListTable.clearChildren();
			for(SerialRecipe serialRecipe : recipeRequest.recipes) {
				ItemStack result = ItemStack.deserialize(serialRecipe.result);
				ItemStack[] costs = new ItemStack[serialRecipe.costs.length];
				for(int i = 0; i < costs.length; i++)
					costs[i] = ItemStack.deserialize(serialRecipe.costs[i]);
				
				RecipeSlot slot = new RecipeSlot(new ClientRecipe(result, costs));
				slot.updateCanCraft(inventoryCounts);
				recipes.add(slot);
				recipeListTable.add(slot).row();
			}
			
			recipeListTable.pack();
			recipeListTable.invalidateHierarchy();
			selection = 0;
			itemChanged();
			refreshCraftability(recipeRequest.stockUpdate);
		}
	}
	
	private int getCount(Item item) { return inventoryCounts.getOrDefault(item.getName(), 0); }
	private int getCount(String itemName) { return inventoryCounts.getOrDefault(itemName, 0); }
	
	// called after item selection changes; resets info in InfoTable
	private void itemChanged() {
		Gdx.app.postRunnable(() -> {
			// I probably have to synchronize this manually since it's in a postRunnable.
			ClientRecipe recipe = recipes.get(selection).recipe;
			
			craftableTable.clearChildren();
			craftableTable.add(new ItemIcon(recipe.result.item, recipe.result.count)).row();
			craftableTable.add(makeLabel(recipe.result.item.getName(), false)).row();
			craftableTable.add(resultLabel = makeLabel("Stock: "+getCount(recipe.result.item), false)).row();
			
			costTable.clearChildren();
			costTable.add(makeLabel("Stock", 14));
			costTable.add(makeLabel("Req'd", 18));
			costTable.row();
			costLabels.clear();
			for(ItemStack cost: recipe.costs) {
				Label costLabel = makeLabel(String.valueOf(getCount(cost.item)), false);
				costLabels.put(cost.item.getName(), costLabel);
				costTable.add(costLabel);
				costTable.add(new ItemIcon(cost.item, cost.count));
				costTable.row();
			}
			
			mainGroup.pack();
			layoutActors();
		});
	}
	
	// called after crafting an item
	private synchronized void refreshCraftability(RecipeStockUpdate stocks) {
		// update inventory item count
		inventoryCounts.clear();
		for(int i = 0; i < stocks.inventoryItemCounts.length; i++)
			inventoryCounts.put(stocks.inventoryItemNames[i], stocks.inventoryItemCounts[i]);
		
		// update slot highlights
		for(RecipeSlot slot: recipes)
			slot.updateCanCraft(inventoryCounts);
		
		// update stock labels
		ClientRecipe recipe = recipes.get(selection).recipe;
		if(resultLabel != null)
			resultLabel.setText("Stock: "+getCount(recipe.result.item));
		//noinspection KeySetIterationMayUseEntrySet
		for(String name: costLabels.keySet())
			costLabels.get(name).setText(String.valueOf(getCount(name)));
	}
	
	@Override
	public synchronized void draw() {
		super.draw();
	}
	
	@Override
	public void act(float delta) {
		super.act(delta);
		
		if(ClientCore.input.pressingKey(Keys.UP))
			moveSelection(-1);
		if(ClientCore.input.pressingKey(Keys.DOWN))
			moveSelection(1);
	}
	
	private synchronized void moveSelection(int amt) {
		if(recipes.size() == 0) return;
		int newSel = selection + amt;
		while(newSel < 0) newSel += recipes.size();
		selection = newSel % recipes.size();
		scrollPane.setSmoothScrolling(false);
		scrollPane.scrollTo(0, recipes.get(selection).getY(), 0, ItemSlot.HEIGHT);
		scrollPane.updateVisualScroll();
		scrollPane.setSmoothScrolling(true);
		itemChanged();
	}
	
	
	private static class ClientRecipe extends Item {
		
		private final ItemStack result;
		private final ItemStack[] costs;
		private boolean canCraft;
		
		ClientRecipe(@NotNull ItemStack result, ItemStack... costs) {
			super(result.item.getName(), result.item.getTexture());
			this.result = result;
			this.costs = costs;
		}
		
		public void updateCanCraft(HashMap<String, Integer> itemCounts) {
			boolean canCraft = true;
			for(ItemStack cost: costs) {
				if(itemCounts.getOrDefault(cost.item.getName(), 0) < cost.count) {
					canCraft = false;
					break;
				}
			}
			
			this.canCraft = canCraft;
		}
	}
	
	private static final class RecipeSlot extends ItemSlot {
		
		private final ClientRecipe recipe;
		
		RecipeSlot(ClientRecipe recipe) {
			super(true, recipe.result.item, recipe.result.count);
			this.recipe = recipe;
		}
		
		public void updateCanCraft(HashMap<String, Integer> itemCounts) {
			recipe.updateCanCraft(itemCounts);
			setBackground(new ColorBackground(this, recipe.canCraft ? craftableBackground : notCraftableBackground));
		}
	}
}
