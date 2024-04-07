package net.nothingtv.game.network.data;

import net.nothingtv.game.network.NVector3;

public class GameCharacter {
    public int id;
    public String name;
    public final NVector3 pos = new NVector3();
    public final NVector3 direction = new NVector3();
}
