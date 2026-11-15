package com.selectivehider;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Wipes the interface when "Interface" is unticked, leaving only the 3D scene.
 */
class SelectiveHiderOverlay extends Overlay
{
	private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

	private final Client client;
	private final SelectiveHiderConfig config;

	@Inject
	SelectiveHiderOverlay(Client client, SelectiveHiderConfig config)
	{
		this.client = client;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
		setPriority(PRIORITY_HIGHEST);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (client.isGpu() && !config.showUi())
		{
			Rectangle bounds = g.getClipBounds();
			if (bounds != null)
			{
				g.setBackground(TRANSPARENT);
				g.clearRect(0, 0, bounds.width, bounds.height);
				// clearRect's background can be system dependent, so also fill with transparency
				g.setColor(TRANSPARENT);
				g.fillRect(0, 0, bounds.width, bounds.height);
			}
		}
		return null;
	}
}
