/* WorldData.java

   by Vadim Kyrylov
   February 2006
   
*/

package tos_teams.africa;

import soccer.common.*;

import java.util.*;
import java.io.*;

/**
 * This class wraps around the SeeData class and provides the visual 
 * perception of the world by the player  who assumes that his team 
 * is playing on the left side. If this is indeed not the case, 
 * the appropriate transformation of coordianates is made. 
 * Some public members of the parent class have been reassigned  
 * self-explanatory names.
 */
 
public class WorldData extends SeeData
{	
	// the player who percieves this information
	private Player me;

	// a list of positions for my team, assuming that it is on the left side. 
	private Vector<Player> myTeam = new Vector<Player>(); 
	
	// a list of positions for the opponent team, (on the right side). 
	private Vector<Player> theirTeam = new Vector<Player>(); 
	
	private Ball ball;	// overrides the ball in the parent class
	
	// the side on which my team is actually playing 
	private char myside;	
	private Transceiver transceiver;
	
	
	public WorldData( SeeData sd, char side, Transceiver tr )
	{
		super(sd.time, sd.player, sd.status, sd.ball, 
							sd.leftTeam, sd.rightTeam);
		myside = side; 
		transceiver = tr;
		transformCoodinates(); 
	} 
	
	
	// this method copies the coordianates of the ball and teams into 
	// new data objects as perceived by me if I were playing on the left side
	
	private void transformCoodinates()
	{
		// create a copy of the ball data
		this.ball =  new Ball(
						getRealPos( myside, super.ball.position ), 
						super.ball.controllerType, 
						super.ball.controllerId ); 
		
		// create a copy of the own team
		Vector team1;
		if ( myside == 'l' )
			team1 = leftTeam;
		else 
			team1 = rightTeam;
			
		//System.out.println(me.id + "-" + myside 
				//+ " leftTeam.size() = " + leftTeam.size() 
				//+ " rightTeam.size() = " + rightTeam.size() );

		myTeam = new Vector<Player>();
		for ( int i = 0; i < team1.size(); i++ ) {
			Player plr = (Player)team1.elementAt( i );
			Vector2d pos = new Vector2d( getRealPos( myside, plr.position ) ); 
			double dir = getRealDir( myside, plr.direction );
			Player teammate = new Player(plr.side, plr.id, pos, dir ); 
			myTeam.addElement( teammate );	
		}

		// create a copy of the opponent team
		Vector team2;
		if ( myside == 'r' )
			team2 = leftTeam;
		else 
			team2 = rightTeam;
		
		theirTeam = new Vector<Player>();
		for ( int i = 0; i < team2.size(); i++ ) {
			Player plr = (Player)team2.elementAt( i );
			Vector2d pos = new Vector2d( getRealPos( myside, plr.position ) ); 
			double dir = getRealDir( myside, plr.direction );
			Player opponent = new Player(plr.side, plr.id, pos, dir ); 
			theirTeam.addElement( opponent );	
		}

		Vector2d mypos = new Vector2d( getRealPos( myside, 
											super.player.position ) ); 
		double mydir = getRealDir( myside, super.player.direction );
		me = new Player(super.player.side, 
							super.player.id, mypos, mydir ); 
		myTeam.addElement( me );	// I add myself, as the server skips me
		
		//System.out.println(me.id + "-" + myside + " myTeam.size() = " + myTeam.size() );
		//System.out.println(me.id + "-" + myside + " theirTeam.size() = " + theirTeam.size() );
	}
	
	
	// returns inverted position 
	public static Vector2d getRealPos( char side, Vector2d pos )
	{
		if ( side == 'l' )
			return new Vector2d( pos );
		else 
			// invert  coordiantes
			return new Vector2d( -pos.getX(),
								 -pos.getY() );
	}
	
	
	// returns inverted direction
	public static double getRealDir(  char side, double dir )
	{
		if ( side == 'l' )
			return dir;
		else 
			// invert direction
			return Util.normal_dir( dir + 180.0 );
	}

		
	// this is a wrap-up method for sending data in the correct coordinates
	public void send( Packet p ) throws IOException
	{
		// trasform coordiantes back to the true side
		switch ( p.packetType ) {
			case Packet.KICK:
				KickData aKickData = (KickData)p.data;
				aKickData.dir = getRealDir( myside, aKickData.dir );
			break;
			
			case Packet.DRIVE:
				DriveData aDriveData = (DriveData)p.data;
				aDriveData.dir = getRealDir( myside, aDriveData.dir );
			break;

			case Packet.TELEPORT:
				TeleportData aTeleportData = (TeleportData)p.data;
				aTeleportData.newX = -aTeleportData.newX;
				aTeleportData.newY = -aTeleportData.newY;
			break;
		} 
		
		transceiver.send( p );	
	}
	
	public Ball getBall() 
	{
		return ball;	
	}

	public Vector<Player> getMyTeam() 
	{
		return myTeam;	
	}

	public Vector<Player> getTheirTeam() 
	{
		return theirTeam;	
	}

	public Player getMyself() 
	{
		return me;	
	}
}
