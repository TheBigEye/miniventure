package miniventure.game.item;

import javax.swing.Timer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

import miniventure.game.core.ClientCore;
import miniventure.game.core.FontStyle;
import miniventure.game.core.InputHandler.Control;
import miniventure.game.network.GameProtocol.*;
import miniventure.game.screen.MenuScreen;
import miniventure.game.screen.util.ColorBackground;
import miniventure.game.util.MyUtils;
import miniventure.game.util.RelPos;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.VisUI;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
	private RecipeUpdate update;
	
	private ArrayList<RecipeSlot> recipes = null;
	private final HashMap<String, Integer> inventoryCounts = new HashMap<>();
	
	private final Table mainGroup;
	
	private final Table craftableTable;
	private final Table costTable;
	private Label resultStockLabel;
	private final HashMap<String, Label> costStockLabels = new HashMap<>();
	private final HashMap<String, ItemSlot> costSlots = new HashMap<>();
	
	private final ScrollPane scrollPane;
	private final Table recipeListTable;
	
	private int selection;
	
	public CraftingScreen() { this(null); }
	public CraftingScreen(@Nullable RecipeUpdate update) {
		super();
		
		mainGroup = useTable(Align.topLeft, false);
		addMainGroup(mainGroup, RelPos.TOP_LEFT);
		
		mainGroup.add(makeLabel("personal crafting", FontStyle.CrafterHeader, false)).row();
		
		craftableTable = new Table(VisUI.getSkin());
		craftableTable.defaults().pad(2f);
		craftableTable.pad(10f);
		craftableTable.add(makeLabel("Waiting for crafting data...", FontStyle.KeepSize, false));
		
		costTable = new Table(VisUI.getSkin());
		costTable.pad(5f);
		costTable.defaults().pad(2f).align(Align.center);
		costTable.add(makeLabel("Waiting for crafting data...", FontStyle.KeepSize, false));
		
		recipeListTable = new Table(VisUI.getSkin()) {
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
				if(Control.CANCEL.matches(keycode) || Control.CRAFTING_TOGGLE.matches(keycode)) {
					ClientCore.removeScreen();
					return true;
				}
				
				synchronized (CraftingScreen.this) {
					if(recipes != null && recipes.size() > 0) {
						if(Control.CONFIRM.matches(keycode)) {
							recipes.get(selection).recipe.onSelect();
							return true;
						}
					}
				}
				
				return false;
			}
		});
		
		scrollPane = new ScrollPane(recipeListTable, VisUI.getSkin()) {
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
		
		mainGroup.setVisible(false);
		Timer t = new Timer(200, e -> mainGroup.setVisible(true));
		t.setRepeats(false);
		t.start();
		
		setKeyboardFocus(recipeListTable);
		setScrollFocus(scrollPane);
		
		if(update != null) {
			this.update = update;
		}
	}
	
	// should only be called by the LibGDX Application thread
	@Override
	public synchronized void focus() {
		super.focus();
		if(!requested) {
			requested = true;
			if(update != null)
				recipeUpdate(update);
			else
				ClientCore.getClient().send(DatalessRequest.Recipes);
		}
	}
	
	// should only be called by GameClient thread, or the libGDX thread.
	public void recipeUpdate(RecipeUpdate recipeRequest) {
		if(ClientCore.getScreen() != this)
			return; // don't care
		
		synchronized (this) {
			recipes = new ArrayList<>(recipeRequest.recipes.length);
			recipeListTable.clearChildren();
			for(SerialRecipe serialRecipe : recipeRequest.recipes) {
				ItemStack[] costs = new ItemStack[serialRecipe.costs.length];
				for(int i = 0; i < costs.length; i++)
					costs[i] = ClientItem.deserializeStack(serialRecipe.costs[i]);
				
				ItemStack result = ClientItem.deserializeStack(serialRecipe.result);
				ClientRecipe recipe = new ClientRecipe(serialRecipe.setOrdinal, serialRecipe.recipeIndex, (ClientItem) result.item, result.count, costs);
				
				RecipeSlot slot = new RecipeSlot(recipe);
				slot.addListener(new InputListener() {
					@Override
					public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
						synchronized (CraftingScreen.this) {
							selection = recipes.indexOf(slot);
							itemChanged();
						}
					}
				});
				slot.addListener(new ClickListener() {
					@Override
					public void clicked(InputEvent event, float x, float y) {
						slot.recipe.onSelect();
					}
				});
				slot.updateCanCraft(inventoryCounts);
				// if(GameCore.debug && slot.recipe.canCraft)
				// 	System.out.println("recipe "+slot.recipe.id+" is craftable");
				recipes.add(slot);
			}
			
			selection = 0;
			itemChanged();
			refreshCraftability(recipeRequest.stockUpdate);
			
			// sort recipes by craftability, craftable first
			recipes.sort(Comparator.comparing(slot -> slot.recipe.canCraft,
				Comparator.comparingInt(canCraft -> canCraft ? 0 : 1)
			));
			
			// if(GameCore.debug)
			// 	System.out.println("recipe display order: " + Arrays.toString(ArrayUtils.mapArray(recipes.toArray(), Integer.class, recipe -> ((RecipeSlot)recipe).recipe.id)));
			
			// add sorted recipes to ui
			for(RecipeSlot slot: recipes)
				recipeListTable.add(slot).row();
			
			recipeListTable.pack();
			recipeListTable.invalidateHierarchy();
			
			Gdx.app.postRunnable(() -> mainGroup.setVisible(true)); // if the timer hasn't yet expired by now
		}
	}
	
	private int getCount(Item item) { return inventoryCounts.getOrDefault(item.getName(), 0); }
	private int getCount(String itemName) { return inventoryCounts.getOrDefault(itemName, 0); }
	
	// called after item selection changes; resets info in InfoTable
	private void itemChanged() {
		Gdx.app.postRunnable(() -> {
			// I probably have to synchronize this manually since it's in a postRunnable.
			ClientRecipe recipe;
			synchronized (CraftingScreen.this) {
				recipe = recipes.get(selection).recipe;
				
				craftableTable.clearChildren();
				craftableTable.add(new ItemIcon(recipe.item, recipe.count)).row();
				craftableTable.add(makeLabel(recipe.item.getName(), FontStyle.KeepSize, false)).row();
				String stockText = recipe.isItemRecipe() ? "Stock: " + getCount(recipe.item) : "";
				craftableTable.add(resultStockLabel = makeLabel(stockText, FontStyle.KeepSize, false)).row();
			}
			
			costTable.clearChildren();
			costTable.add(makeLabel("Stock", FontStyle.StockHeader, false));
			costTable.add(makeLabel("Required", FontStyle.CostHeader, false));
			costTable.row();
			costSlots.clear();
			for(ItemStack cost : recipe.costs) {
				Label costLabel = makeLabel(String.valueOf(getCount(cost.item)), FontStyle.KeepSize, false);
				costStockLabels.put(cost.item.getName(), costLabel);
				costTable.add(costLabel);
				
				Color background = getCount(cost.item) >= cost.count ? craftableBackground : notCraftableBackground;
				ItemSlot slot = new ItemSlot(true, cost.item, cost.count, background);
				costSlots.put(cost.item.getName(), slot);
				costTable.add(slot).align(Align.left);
				costTable.row();
			}
			
			layoutActors();
		});
	}
	
	// called after crafting an item
	public synchronized void refreshCraftability(RecipeStockUpdate stocks) {
		// update inventory item count
		inventoryCounts.clear();
		for(int i = 0; i < stocks.inventoryItemCounts.length; i++)
			inventoryCounts.put(stocks.inventoryItemNames[i], stocks.inventoryItemCounts[i]);
		
		// update slot highlights
		for(RecipeSlot slot: recipes)
			slot.updateCanCraft(inventoryCounts);
		
		// update stock labels
		ClientRecipe recipe = recipes.get(selection).recipe;
		if(resultStockLabel != null)
			resultStockLabel.setText(recipe.isItemRecipe() ? "Stock: "+getCount(recipe.item) : "");
		//noinspection KeySetIterationMayUseEntrySet
		for(String name: costSlots.keySet()) {
			Label costStockLabel = costStockLabels.get(name);
			costStockLabel.setText(String.valueOf(getCount(name)));
			
			ItemSlot slot = costSlots.get(name);
			Color background = getCount(name) >= slot.getCount() ? craftableBackground : notCraftableBackground; 
			slot.setBackground(new ColorBackground(slot, background));
		}
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
		selection = MyUtils.wrapIndex(selection + amt, recipes.size());
		scrollPane.setSmoothScrolling(false);
		scrollPane.scrollTo(0, recipes.get(selection).getY(), 0, ItemSlot.HEIGHT);
		scrollPane.updateVisualScroll();
		scrollPane.setSmoothScrolling(true);
		itemChanged();
	}
	
	
	private static class ClientRecipe {
		
		private final int setOrdinal;
		private final int id;
		private final ClientItem item;
		private final int count;
		private final ItemStack[] costs;
		private boolean canCraft;
		
		ClientRecipe(int setOrdinal, int id, @NotNull ClientItem model, int count, ItemStack[] costs) {
			this.setOrdinal = setOrdinal;
			this.id = id;
			this.item = model;
			this.count = count;
			this.costs = costs;
		}
		
		private boolean isItemRecipe() { return count > 0; }
		
		void onSelect() {
			ClientCore.getClient().send(new RecipeSelectionRequest(isItemRecipe(), setOrdinal, id));
			if(!isItemRecipe())
				ClientCore.removeScreen();
		}
		
		/*boolean needsItem(Item item) {
			for(ItemStack stack: costs)
				if(stack.item.equals(item))
					return true;
			
			return false;
		}*/
		
		void updateCanCraft(HashMap<String, Integer> itemCounts) {
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
			super(true, recipe.item, recipe.count);
			this.recipe = recipe;
		}
		
		public void updateCanCraft(HashMap<String, Integer> itemCounts) {
			recipe.updateCanCraft(itemCounts);
			setBackground(new ColorBackground(this, recipe.canCraft ? craftableBackground : notCraftableBackground));
		}
	}
}
