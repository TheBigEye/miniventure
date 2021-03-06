package miniventure.game.network;

import miniventure.game.network.PacketPipe.PacketPipeReader;
import miniventure.game.network.PacketPipe.PacketPipeWriter;

import org.jetbrains.annotations.NotNull;

public class LocalClient extends GameClient {
	
	private final PacketPipeReader fromServer;
	private final PacketPipeWriter toServer;
	
	public LocalClient(@NotNull PacketPipeReader fromServer, @NotNull PacketPipeWriter toServer) {
		this.fromServer = fromServer;
		this.toServer = toServer;
		
		fromServer.setListener(packet -> handlePacket(packet, toServer));
	}
	
	@Override
	public void send(Object obj) { toServer.send(obj); }
	
	@Override
	public void disconnect() {
		toServer.close();
		fromServer.close();
	}
}
