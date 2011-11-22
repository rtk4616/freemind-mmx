package plugins.map;

//License: GPL. Copyright 2008 by Jan Peter Stotz

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import org.openstreetmap.gui.jmapviewer.Coordinate;
import org.openstreetmap.gui.jmapviewer.JMapViewer;
import org.openstreetmap.gui.jmapviewer.MemoryTileCache;
import org.openstreetmap.gui.jmapviewer.OsmFileCacheTileLoader;
import org.openstreetmap.gui.jmapviewer.OsmTileLoader;
import org.openstreetmap.gui.jmapviewer.events.JMVCommandEvent;
import org.openstreetmap.gui.jmapviewer.interfaces.JMapViewerEventListener;

import plugins.map.MapNodePositionHolder.MapNodePositionListener;
import freemind.controller.MapModuleManager.MapModuleChangeObserver;
import freemind.controller.actions.generated.instance.MapWindowConfigurationStorage;
import freemind.main.Resources;
import freemind.main.Tools;
import freemind.modes.MindMapNode;
import freemind.modes.Mode;
import freemind.modes.ModeController.NodeSelectionListener;
import freemind.modes.mindmapmode.MindMapController;
import freemind.modes.mindmapmode.hooks.MindMapHookAdapter;
import freemind.view.MapModule;
import freemind.view.mindmapview.NodeView;

/**
 * 
 * Demonstrates the usage of {@link JMapViewer}
 * 
 * @author Jan Peter Stotz adapted for FreeMind by Chris.
 */
public class MapDialog extends MindMapHookAdapter implements
		JMapViewerEventListener, MapModuleChangeObserver,
		MapNodePositionListener, NodeSelectionListener {

	private static final String WINDOW_PREFERENCE_STORAGE_PROPERTY = MapDialog.class
			.getName();

	private static final String TILE_CACHE_CLASS = "tile_cache_class";

	private static final String FILE_TILE_CACHE_DIRECTORY = "file_tile_cache_directory";

	private JCursorMapViewer map = null;

	private JLabel zoomLabel = null;
	private JLabel zoomValue = null;

	private JLabel mperpLabelName = null;
	private JLabel mperpLabelValue = null;

	private MindMapController mMyMindMapController;

	private JDialog mMapDialog;

	private HashMap /* < MapNodePositionHolder, MapMarkerLocation > */mMarkerMap = new HashMap();

	/*
	 * (non-Javadoc)
	 * 
	 * @see freemind.extensions.HookAdapter#startupMapHook()
	 */
	public void startupMapHook() {
		super.startupMapHook();
		mMyMindMapController = super.getMindMapController();
		getMindMapController().getController().getMapModuleManager()
				.addListener(this);
		mMapDialog = new JDialog(getController().getFrame().getJFrame(), false /* unmodal */);
		mMapDialog.setTitle(getResourceString("MapDialog_title"));
		mMapDialog
				.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		mMapDialog.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent event) {
				disposeDialog();
			}
		});
		Tools.addEscapeActionToDialog(mMapDialog, new AbstractAction() {
			public void actionPerformed(ActionEvent arg0) {
				disposeDialog();
			}
		});
		mMapDialog.setSize(400, 400);

		
		
		map = new JCursorMapViewer(getMindMapController(), mMapDialog,
				new MemoryTileCache(), this);
		map.addJMVListener(this);
		OsmTileLoader loader = null;
		String tileCacheClass = Resources.getInstance().getProperty(
				TILE_CACHE_CLASS);
		if (Tools.safeEquals(tileCacheClass, "file")) {
			String directory = Resources.getInstance()
					.getProperty(FILE_TILE_CACHE_DIRECTORY);
			if (directory.startsWith("%/")) {
				directory = Resources.getInstance().getFreemindDirectory()
						+ File.separator + directory.substring(2);
			}
			logger.info("Trying to use file cache tile loader with dir " + directory);
			try {
				loader = new OsmFileCacheTileLoader(map, new File(directory));
			} catch (SecurityException e1) {
				freemind.main.Resources.getInstance().logException(e1);
				
			} catch (IOException e1) {
				freemind.main.Resources.getInstance().logException(e1);
				
			}
		}
		if (loader == null) {
			logger.info("Using osm tile loader");
			loader = new OsmTileLoader(map);
		}
		map.setTileLoader(loader);

		// Listen to the map viewer for user operations so components will
		// receive events and update

		mMapDialog.setLayout(new BorderLayout());
		JPanel panel = new JPanel();
		JPanel helpPanel = new JPanel();

		mperpLabelName = new JLabel("Meters/Pixels: ");
		mperpLabelValue = new JLabel(format("%s", map.getMeterPerPixel()));

		zoomLabel = new JLabel("Zoom: ");
		zoomValue = new JLabel(format("%s", map.getZoom()));

		mMapDialog.add(panel, BorderLayout.NORTH);
		mMapDialog.add(helpPanel, BorderLayout.SOUTH);
		JLabel helpLabel = new JLabel("Use left mouse button to move,\n "
				+ "mouse wheel to zoom, left click to set cursor.");
		helpPanel.add(helpLabel);

		panel.add(zoomLabel);
		panel.add(zoomValue);
		panel.add(mperpLabelName);
		panel.add(mperpLabelValue);

		mMapDialog.add(map, BorderLayout.CENTER);

		// add known markers to the map.
		Set mapNodePositionHolders = getMapNodePositionHolders();
		for (Iterator it = mapNodePositionHolders.iterator(); it.hasNext();) {
			MapNodePositionHolder nodePositionHolder = (MapNodePositionHolder) it
					.next();
			addMapMarker(nodePositionHolder);
		}
		((Registration) getPluginBaseClass())
				.registerMapNodePositionListener(this);
		getMindMapController().registerNodeSelectionListener(this, true);

		map.setCursorPosition(new Coordinate(49.8, 8.8));
		map.setUseCursor(true);
		// restore preferences:
		// Retrieve window size and column positions.
		MapWindowConfigurationStorage storage = (MapWindowConfigurationStorage) getMindMapController()
				.decorateDialog(mMapDialog, WINDOW_PREFERENCE_STORAGE_PROPERTY);
		if (storage != null) {
			map.setDisplayPositionByLatLon(storage.getMapCenterLatitude(),
					storage.getMapCenterLongitude(), storage.getZoom());
			map.setCursorPosition(new Coordinate(storage.getCursorLatitude(),
					storage.getCursorLongitude()));
			map.getFreeMindMapController().changeTileSource(
					storage.getTileSource(), map);
		}
		mMapDialog.setVisible(true);

	}

	public Set getMapNodePositionHolders() {
		return ((Registration) getPluginBaseClass())
				.getMapNodePositionHolders();
	}

	public void addMapMarker(MapNodePositionHolder nodePositionHolder) {
		Coordinate position = nodePositionHolder.getPosition();
		logger.info("Adding map position for " + nodePositionHolder.getNode()
				+ " at " + position);
		MapMarkerLocation marker = new MapMarkerLocation(nodePositionHolder);
		marker.setSize(marker.getPreferredSize());
		map.addMapMarker(marker);
		mMarkerMap.put(nodePositionHolder, marker);
	}

	/**
	 * Overwritten, as this dialog is not modal, but after the plugin has
	 * terminated, the dialog is still present and needs the controller to store
	 * its values.
	 * */
	public MindMapController getMindMapController() {
		return mMyMindMapController;
	}

	/**
	 * 
	 */
	protected void disposeDialog() {
		Registration registration = (Registration) getPluginBaseClass();
		if (registration != null) {
			// on close, it is null. Why?
			registration.deregisterMapNodePositionListener(this);
		}
		getMindMapController().deregisterNodeSelectionListener(this);

		// store window positions:
		MapWindowConfigurationStorage storage = new MapWindowConfigurationStorage();
		// Set coordinates
		storage.setZoom(map.getZoom());
		Coordinate position = map.getPosition();
		storage.setMapCenterLongitude(position.getLon());
		storage.setMapCenterLatitude(position.getLat());
		Coordinate cursorPosition = map.getCursorPosition();
		storage.setCursorLongitude(cursorPosition.getLon());
		storage.setCursorLatitude(cursorPosition.getLat());
		storage.setTileSource(map.getTileController().getTileSource()
				.getClass().getName());
		getMindMapController().storeDialogPositions(mMapDialog, storage,
				WINDOW_PREFERENCE_STORAGE_PROPERTY);

		getMindMapController().getController().getMapModuleManager()
				.removeListener(this);
		mMapDialog.setVisible(false);
		mMapDialog.dispose();
	}

	private void updateZoomParameters() {
		if (mperpLabelValue != null)
			mperpLabelValue.setText(format("%s", map.getMeterPerPixel()));
		if (zoomValue != null)
			zoomValue.setText(format("%s", map.getZoom()));
	}

	/**
	 * @param pString
	 * @param pMeterPerPixel
	 * @return
	 */
	private String format(String pString, double pObject) {
		return "" + pObject;
	}

	public void processCommand(JMVCommandEvent command) {
		if (command.getCommand().equals(JMVCommandEvent.COMMAND.ZOOM)
				|| command.getCommand().equals(JMVCommandEvent.COMMAND.MOVE)) {
			updateZoomParameters();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see freemind.controller.MapModuleManager.MapModuleChangeObserver#
	 * isMapModuleChangeAllowed(freemind.view.MapModule, freemind.modes.Mode,
	 * freemind.view.MapModule, freemind.modes.Mode)
	 */
	public boolean isMapModuleChangeAllowed(MapModule pOldMapModule,
			Mode pOldMode, MapModule pNewMapModule, Mode pNewMode) {
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see freemind.controller.MapModuleManager.MapModuleChangeObserver#
	 * beforeMapModuleChange(freemind.view.MapModule, freemind.modes.Mode,
	 * freemind.view.MapModule, freemind.modes.Mode)
	 */
	public void beforeMapModuleChange(MapModule pOldMapModule, Mode pOldMode,
			MapModule pNewMapModule, Mode pNewMode) {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * freemind.controller.MapModuleManager.MapModuleChangeObserver#afterMapClose
	 * (freemind.view.MapModule, freemind.modes.Mode)
	 */
	public void afterMapClose(MapModule pOldMapModule, Mode pOldMode) {
		disposeDialog();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see freemind.controller.MapModuleManager.MapModuleChangeObserver#
	 * afterMapModuleChange(freemind.view.MapModule, freemind.modes.Mode,
	 * freemind.view.MapModule, freemind.modes.Mode)
	 */
	public void afterMapModuleChange(MapModule pOldMapModule, Mode pOldMode,
			MapModule pNewMapModule, Mode pNewMode) {
		disposeDialog();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see freemind.controller.MapModuleManager.MapModuleChangeObserver#
	 * numberOfOpenMapInformation(int, int)
	 */
	public void numberOfOpenMapInformation(int pNumber, int pIndex) {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * plugins.map.MapNodePositionHolder.MapNodePositionListener#registerMapNode
	 * (plugins.map.MapNodePositionHolder)
	 */
	public void registerMapNode(MapNodePositionHolder pMapNodePositionHolder) {
		addMapMarker(pMapNodePositionHolder);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * plugins.map.MapNodePositionHolder.MapNodePositionListener#deregisterMapNode
	 * (plugins.map.MapNodePositionHolder)
	 */
	public void deregisterMapNode(MapNodePositionHolder pMapNodePositionHolder) {
		MapMarkerLocation marker = (MapMarkerLocation) mMarkerMap
				.remove(pMapNodePositionHolder);
		if (marker != null) {
			map.removeMapMarker(marker);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * freemind.modes.ModeController.NodeSelectionListener#onUpdateNodeHook(
	 * freemind.modes.MindMapNode)
	 */
	public void onUpdateNodeHook(MindMapNode pNode) {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * freemind.modes.ModeController.NodeSelectionListener#onSelectHook(freemind
	 * .view.mindmapview.NodeView)
	 */
	public void onFocusNode(NodeView pNode) {
	}

	public void selectMapPosition(NodeView pNode, boolean sel) {
		// test for map position:
		MapNodePositionHolder hook = MapNodePositionHolder.getHook(pNode
				.getModel());
		if (hook != null) {
			if (mMarkerMap.containsKey(hook)) {
				MapMarkerLocation location = (MapMarkerLocation) mMarkerMap
						.get(hook);
				location.setSelected(sel);
				map.repaint();
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * freemind.modes.ModeController.NodeSelectionListener#onDeselectHook(freemind
	 * .view.mindmapview.NodeView)
	 */
	public void onLostFocusNode(NodeView pNode) {

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * freemind.modes.ModeController.NodeSelectionListener#onSaveNode(freemind
	 * .modes.MindMapNode)
	 */
	public void onSaveNode(MindMapNode pNode) {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * freemind.modes.ModeController.NodeSelectionListener#onSelectionChange
	 * (freemind.modes.MindMapNode, boolean)
	 */
	public void onSelectionChange(NodeView pNode, boolean pIsSelected) {
		selectMapPosition(pNode, pIsSelected);

	}
}
