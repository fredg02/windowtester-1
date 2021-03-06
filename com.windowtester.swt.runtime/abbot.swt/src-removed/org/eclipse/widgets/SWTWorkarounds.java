package org.eclipse.swt.widgets;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;

import abbot.Platform;

import com.windowtester.swt.platform.ext.macosx.MacExtensions;

/**
 * This class adapts SWT to Abbot, e.g. where SWT methods are not public.
 * 
 * @author Gary Johnston
 * @author Steve Northover
 * @version $Id: SWTWorkarounds.java,v 1.15 2009-01-26 17:46:22 pq Exp $
 */
public class SWTWorkarounds {

	/**
	 * The MacExtensions instance. It is initialized during plug-in activation
	 * when running Mac OSX. It will be null otherwise.
	 * @see com.windowtester.swt.RuntimePlugin#start(org.osgi.framework.BundleContext)
	 */
	public static MacExtensions MacExt;

	/*************************** COMMON *****************************/	
	public static Rectangle getBounds (Object object) {
		Rectangle result = new Rectangle (0, 0, 0, 0);
		try {
			Method method = object.getClass().getDeclaredMethod ("getBounds", null);
			method.setAccessible(true);
			result = (Rectangle) method.invoke (object, null);
		} catch (Throwable th) {
			// TODO - decide what should happen when the method is unavailable
		}
		return result;
	}
	
	public static Rectangle getBounds(MenuItem menuItem) {
		if (Platform.isOSX()) {
			Rectangle result = MacExt.getMenuItemBounds(menuItem);
			if (menuItem.isDisposed()) {
				throw new NullPointerException(); // expected to be caught in WidgetLocator.getLocation(Widget, boolean)
			}
			return result;
		}
		Rectangle itemRect = getBounds ((Object)menuItem);
		Rectangle menuRect = getBounds (menuItem.getParent ());
		if ((menuItem.getParent ().getStyle() & SWT.RIGHT_TO_LEFT) != 0) {
			itemRect.x = menuRect.x + menuRect.width - itemRect.width - itemRect.x;
		} else {
			itemRect.x += menuRect.x;
		}
		// https://bugs.eclipse.org/bugs/show_bug.cgi?id=38436#c143
		itemRect.y += menuRect.y;
		return itemRect;
	}

	public static Rectangle getBounds (Menu menu) {
		return getBounds ((Object)menu);
	}

	public static Rectangle getBounds (ScrollBar scrollBar) {
		Point size = scrollBar.getSize ();
		Rectangle bounds = scrollBar.getParent().getBounds();
		if ((scrollBar.getParent ().getStyle() & SWT.RIGHT_TO_LEFT) != 0) {
			bounds.x = 0;
			bounds.width = size.x;
		} else {
			bounds.x = bounds.width - size.x;
		}
		bounds.y = bounds.height - size.y;
		// TODO - coordinate system may change when the API is added to SWT
		return scrollBar.getDisplay().map (scrollBar.getParent (), null, bounds);
	}
	
	/*************************** WIN32 *****************************/
	static int SendMessage (long hWnd, int Msg, int wParam, int [] lParam)
	{
		/*
		 * In the move to 3.5M4 we're seeing this error in the build:
		 * SendMessage(int,int,int,int[]) in SWTWorkarounds cannot be applied to (long,int,int,int[])
		 * This cast fixes the compilation.  NOTE: this method should not get called in 3.5.
		 */
		return SendMessage((int)hWnd, Msg, wParam, lParam);
	}
	
	
	static int SendMessage (int hWnd, int Msg, int wParam, int [] lParam)
	{
		int result = 0;
		try {
			Class clazz = Class.forName ("org.eclipse.swt.internal.win32.OS");
			Class [] params = new Class [] {
				Integer.TYPE,
				Integer.TYPE,
				Integer.TYPE,
				lParam.getClass (),
			};
			Method method = clazz.getMethod ("SendMessage", params);
			Object [] args = new Object [] {
				new Integer (hWnd),
				new Integer (Msg),
				new Integer (wParam),
				lParam,
			};
			result = ((Integer) method.invoke (clazz, args)).intValue ();
		} catch (Throwable e) {
			// TODO - decide what should happen when the method is unavailable
		}
		return result;
	}

	static Rectangle win32_getBounds(TabItem tabItem) {
		TabFolder parent = tabItem.getParent();
		int index = parent.indexOf (tabItem);
		if (index == -1) return new Rectangle (0, 0, 0, 0);
		int [] rect = new int [4];
		SendMessage (parent.handle, /*TCM_GETITEMRECT*/ 0x130a, index, rect);
		int width = rect [2] - rect[0];
		int height = rect [3] - rect [1];
		Rectangle bounds = new Rectangle (rect [0], rect [1], width, height);
		return tabItem.getDisplay().map (tabItem.getParent (), null, bounds);
	}

	static Rectangle win32_getBounds(TableColumn tableColumn) {
		Table parent = tableColumn.getParent ();
		int index = parent.indexOf (tableColumn);
		if (index == -1) return new Rectangle (0, 0, 0, 0); 
		int hwndHeader = SendMessage (parent.handle, /*LVM_GETHEADER*/ 0x101f, 0, new int [0]);		
		int [] rect = new int [4];
		SendMessage (hwndHeader, /*HDM_GETITEMRECT*/ 0x1200 + 7, index, rect);
		int width = rect [2] - rect[0];
		int height = rect [3] - rect [1];
		Rectangle bounds = new Rectangle (rect [0], rect [1], width, height);
		// TODO - oordinate system may change when the API is added to SWT
		return tableColumn.getDisplay().map (parent, null, bounds);
	}
	
	/*************************** GTK *****************************/
	static void gtk_getBounds (int handle, Rectangle bounds) {	
		try {
			Class clazz = Class.forName ("org.eclipse.swt.internal.gtk.OS");
			Class [] params = new Class [] {Integer.TYPE};
			Object [] args = new Object [] {new Integer (handle)};
			Method method = clazz.getMethod ("GTK_WIDGET_X", params);
			bounds.x = ((Integer) method.invoke (clazz, args)).intValue ();
			method = clazz.getMethod ("GTK_WIDGET_Y", params);
			bounds.y = ((Integer) method.invoke (clazz, args)).intValue ();
			method = clazz.getMethod ("GTK_WIDGET_WIDTH", params);
			bounds.width = ((Integer) method.invoke (clazz, args)).intValue ();
			method = clazz.getMethod ("GTK_WIDGET_HEIGHT", params);
			bounds.height = ((Integer) method.invoke (clazz, args)).intValue ();
		} catch (Throwable e) {
			// TODO - decide what should happen when the method is unavailable
		}
	}
	
	static Rectangle gtk_getBounds(TableColumn tabColumn) {
		Rectangle bounds = new Rectangle (0, 0, 0, 0);
		try {
			Class c = tabColumn.getClass();
			Field f = c.getDeclaredField("buttonHandle");
			f.setAccessible(true);
			int handle = f.getInt(tabColumn);			
			gtk_getBounds(handle, bounds);
		} catch (Throwable e) {
			// TODO - decide what should happen when the method is unavailable
		}
		return tabColumn.getDisplay().map (tabColumn.getParent (), null, bounds);
	}
	
	static Rectangle gtk_getBounds(TabItem tabItem) {
		Rectangle bounds = new Rectangle (0, 0, 0, 0);  
		try {
			Class c = Class.forName ("org.eclipse.swt.widgets.Widget");
			Field f = c.getDeclaredField("handle");
			f.setAccessible(true);
			int handle = f.getInt(tabItem);
			gtk_getBounds(handle, bounds);
		} catch (Throwable e) {
			// TODO - decide what should happen when the method is unavailable
		}
		return tabItem.getDisplay().map (tabItem.getParent (), null, bounds);
	}
	
	/*************************** MOTIF  *****************************/
	static Rectangle motif_getBounds(TabItem tabItem) {
		Rectangle bounds = new Rectangle (0, 0, 0, 0);  
		try {
			Class c = tabItem.getClass();
			Method m = c.getDeclaredMethod("getBounds", null);
			m.setAccessible(true);
			bounds = (Rectangle)m.invoke(tabItem, null);
			int margin = 2;
			bounds.x +=margin;bounds.y+=margin;
			bounds.width -= 2*margin; bounds.height-=margin;
		} catch (Throwable e) {
			// TODO - decide what should happen when the method is unavailable
		}
		return tabItem.getDisplay().map (tabItem.getParent (), null, bounds);
	}
	
	static Rectangle motif_getBounds(TableColumn tableColumn) {
		Rectangle bounds = new Rectangle (0, 0, 0, 0);  
		try {
			Class c = tableColumn.getClass();
			Method m = c.getDeclaredMethod("getX", null);
			m.setAccessible(true);
			bounds.x = ((Integer)m.invoke(tableColumn, null)).intValue();
			bounds.width = tableColumn.getWidth() - 2;
			bounds.height = tableColumn.getParent().getHeaderHeight() - 2;
		} catch (Throwable e) {
			// TODO - decide what should happen when the method is unavailable
		}
		return tableColumn.getDisplay().map (tableColumn.getParent (), null, bounds);
	}
	
	/*************************** CARBON  *****************************/
	static Rectangle carbon_getBounds(TabItem tabItem) {
		Rectangle bounds =  MacExt.getTabItemBounds(tabItem);
//		bounds = tabItem.getDisplay().map(tabItem.getParent(), null, bounds);
//		System.out.println(bounds);
		return bounds;
	}	
	
	static Rectangle carbon_getBounds(TableColumn tableColumn) {
		Table table = tableColumn.getParent();
		if (!table.getHeaderVisible())
			return new Rectangle(0, 0, 0, 0);
		TableColumn[] columns = table.getColumns();
		int x = 0;
		int width = 0;
		int height = table.getHeaderHeight();
		for (int i = 0; i < columns.length; i++) {
			TableColumn col = columns[i];
			if (col == tableColumn) {
				width = col.getWidth();
				break;
			} else
				x += col.getWidth();
		}
		if (width == 0)
			return new Rectangle(0, 0, 0, 0);
		Rectangle bounds = new Rectangle(x, 0, width, height);
		bounds = tableColumn.getDisplay().map(table, null, bounds);
		return bounds;
	}

	public static Rectangle getBounds (TabItem tabItem){
		if (SWT.getPlatform().equals("win32")) {
			return win32_getBounds (tabItem);
		}
		if (SWT.getPlatform().equals("gtk")) {
			return gtk_getBounds (tabItem);
		}
		if (SWT.getPlatform().equals("motif")) {
			return motif_getBounds (tabItem);
		}
		if (SWT.getPlatform().equals("carbon")) {
			return carbon_getBounds (tabItem);
		}
		return null;
	}

	public static Rectangle getBounds (TableColumn tableColumn) {
		if (SWT.getPlatform().equals("win32")) {
			return win32_getBounds (tableColumn);
		}
		if (SWT.getPlatform().equals("gtk")) {
			return gtk_getBounds (tableColumn);
		}
		if (SWT.getPlatform().equals("motif")) {
			return motif_getBounds (tableColumn);
		}
		if (SWT.getPlatform().equals("carbon")) {
			return carbon_getBounds (tableColumn);
		}
		return null;
	}

	public static Rectangle getBounds (TableItem item) {
		return item.getDisplay().map (item.getParent (), null, item.getBounds (0));
	}

	public static Rectangle getBounds (TreeItem item) {
		return item.getDisplay().map (item.getParent (), null, item.getBounds ());
	}

	public static Rectangle getBounds (CTabItem item) {
		return item.getDisplay().map (item.getParent (), null, item.getBounds ());
	}

	public static Rectangle getBounds (ToolItem item) {
		return item.getDisplay().map (item.getParent (), null, item.getBounds ());
	}

	public static Rectangle getBounds (CoolItem item) {
		return item.getDisplay().map (item.getParent (), null, item.getBounds ());
	}
	
	//!pq:
	public static MenuItem[] getItems(Display d) {
		MenuItem[] result = null;
		try {			
			Field field = d.getClass().getDeclaredField("items");
			field.setAccessible(true);
			result = (MenuItem[]) field.get(d);
		} catch (Throwable th) {
			th.printStackTrace();
			// TODO - decide what should happen when the method is unavailable
		}
		//prune out null entries
		java.util.List pruned = new ArrayList();
		if (result != null) {
			for (int i = 0; i < result.length; i++) {
				if (result[i] != null)
					pruned.add(result[i]);
			}
		}
		return (MenuItem[])pruned.toArray(new MenuItem[]{});
	}
	
	//!pq:
	//TODO[pq]: move this to a fragment
	public static Menu[] getMenus(Display d) {
		MenuItem[] result = null;
		try {			
			Field field = d.getClass().getDeclaredField("items");
			field.setAccessible(true);
			result = (MenuItem[]) field.get(d);
		} catch (Throwable th) {
			th.printStackTrace();
			// TODO - decide what should happen when the method is unavailable
		}
		LinkedHashSet set = new LinkedHashSet();
		if (result != null) {
			for (int i = 0; i < result.length; i++) {
				if (result[i] != null) {
					Menu menu = (Menu) ((MenuItem) result[i]).getParent();
					// TODO: clean this up!
					if (menu != null && !isSubMenu(menu) && !parentIsControl(menu))
						set.add(menu);
				}
			}
		}
		return (Menu[])set.toArray(new Menu[]{});
	}

	//TODO: fix me... producing duplicates...
	private static boolean isSubMenu(Menu menu) {
		MenuItem parent = menu.getParentItem();
		return parent != null;
	}
	
	private static boolean parentIsControl(Menu menu) {
		Decorations parent = menu.getParent();
		return (parent != null && parent.getClass().equals(Control.class));
	}

	
	
	//!pq:
	public static Menu[] getMenus(Decorations shell) {
		Menu[] result = null;
		try {
			if (Platform.isOSX()) {
				Menu bar = shell.getMenuBar();
				if (bar == null)
					return new Menu[0]; // TODO Mac testing
				MenuItem[] items = bar.getItems();
				result = new Menu[items.length];
				for (int i = 0; i < items.length; i++)
					result[i] = items[i].getMenu();
			} else {
				Field field = Decorations.class.getDeclaredField("menus");
				field.setAccessible(true);
				result = (Menu[]) field.get(shell);
			}
		} catch (Throwable th) {
			th.printStackTrace();
			// TODO - decide what should happen when the method is unavailable
		}
		return result;
	}
	
	//!pq:
	public static Rectangle getBounds(Control w, Layout layout) {
		Rectangle bounds = new Rectangle (0, 0, 0, 0);  
		Point result;
		try {
			Class c = Layout.class;
			Class [] params = new Class [] {
				Composite.class,
				Integer.TYPE,
				Integer.TYPE,
				Boolean.TYPE,
			};
			Method m = c.getDeclaredMethod("computeSize", params);
			m.setAccessible(true);
			Object[] args = {w.getParent(), new Integer(0), new Integer(0), new Boolean(false)};
			result = (Point)m.invoke(layout, args);
			
			
		} catch (Throwable e) {
			// TODO - decide what should happen when the method is unavailable
		}
		return bounds;
		
	}

	/**
	 * Return true if the accessibility API must be enabled. This is
	 * Mac-specific so return false if not on a Mac.
	 * 
	 * @return true if running on Mac and accessibility needs to be enabled
	 */
	public static boolean isMacAccessibilityDisabled() {
		if (Platform.isOSX())
			return !MacExt.isAXAPIEnabled();
		return false;
	}
}


