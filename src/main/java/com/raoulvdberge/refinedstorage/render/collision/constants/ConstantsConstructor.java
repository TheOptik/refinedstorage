package com.raoulvdberge.refinedstorage.render.collision.constants;

import com.raoulvdberge.refinedstorage.render.collision.CollisionGroup;
import com.raoulvdberge.refinedstorage.util.RenderUtils;

public final class ConstantsConstructor {
    public static final CollisionGroup HEAD_NORTH = new CollisionGroup().addItem(RenderUtils.getBounds(2, 2, 0, 14, 14, 2)).setCanAccessGui(true);
    public static final CollisionGroup HEAD_EAST = new CollisionGroup().addItem(RenderUtils.getBounds(14, 2, 2, 16, 14, 14)).setCanAccessGui(true);
    public static final CollisionGroup HEAD_SOUTH = new CollisionGroup().addItem(RenderUtils.getBounds(2, 2, 14, 14, 14, 16)).setCanAccessGui(true);
    public static final CollisionGroup HEAD_WEST = new CollisionGroup().addItem(RenderUtils.getBounds(0, 2, 2, 2, 14, 14)).setCanAccessGui(true);
    public static final CollisionGroup HEAD_DOWN = new CollisionGroup().addItem(RenderUtils.getBounds(2, 0, 2, 14, 2, 14)).setCanAccessGui(true);
    public static final CollisionGroup HEAD_UP = new CollisionGroup().addItem(RenderUtils.getBounds(2, 14, 2, 14, 16, 14)).setCanAccessGui(true);
}
