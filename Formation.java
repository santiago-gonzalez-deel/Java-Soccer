/* Formation.java
 
 by Vadim Kyrylov
 January 2006
 
 This class implements hard coded team formation.
 
 An alternative implementation is possible by manually placing players 
 on the field with the mouse in the Stepwise mode. Then this situation 
 can be saved to a file. This file could be loaded into this class. 
 
 Methods for saving game situation and loading it from file can be found
 in Soccer Monitor ( see SaveSnapshotAction, CoachLoadFileAction classes 
 in package soccer.client.action package and SituationDialog class in 
 soccer.client.dialog package)
 
 
*/


package tos_teams.africa;

import soccer.common.*;
import java.util.*;


// use this template for creating differnt formations and running 
// simulation experiemnets with them

public class Formation
{		
	public Vector fplayers = new Vector();			
	private FPlayer player; 
	
	
	Formation( String formation )
	{
		// all for the left-hand team
		
		if ( formation == "433" )
		{
			// the goalie 
			player = new FPlayer ( new Vector2d( -48.0, 0.0 ), false, false, false, false );
			fplayers.addElement( player );		
			
			// three defenders
			player = new FPlayer ( new Vector2d( -33.0, 20.0 ), true, false, false, false );
			fplayers.addElement( player );		
			player = new FPlayer ( new Vector2d( -35.0, 0.0 ), true, false, false, false );
			fplayers.addElement( player );		
			player = new FPlayer ( new Vector2d( -33.0, -20.0 ), true, false, false, false );
			fplayers.addElement( player );		
				
			// three midfielders
			player = new FPlayer ( new Vector2d( -17.0, 25.0 ), false, true, false, false );
			fplayers.addElement( player );		
			player = new FPlayer ( new Vector2d( -22.0, 0.0 ), false, true, false, false );
			fplayers.addElement( player );		
			player = new FPlayer ( new Vector2d( -17.0, -25.0 ), false, true, false, false );
			fplayers.addElement( player );		

			// four forwards
			player = new FPlayer ( new Vector2d( -1.5, 20.0 ), false, false, true, false );
			fplayers.addElement( player );		
			player = new FPlayer ( new Vector2d( -1.5, 8.0 ), false, false, true, true );	// kicker
			fplayers.addElement( player );		
			player = new FPlayer ( new Vector2d( -1.5, -8.0 ), false, false, true, false ); 
			fplayers.addElement( player );		
			player = new FPlayer ( new Vector2d( -1.5, -20.0 ), false, false, true, false );
			fplayers.addElement( player );		
		}
		else if ( formation == "523" )
		{
			// the "W" formation
			// the goalie 
			player = new FPlayer ( new Vector2d( -48.0, 0.0 ), false, false, false, false );
			fplayers.addElement( player );		

			// three defenders
			player = new FPlayer ( new Vector2d( -33.0, 20.0 ), true, false, false, false );
			fplayers.addElement( player );		
			player = new FPlayer ( new Vector2d( -35.0, 0.0 ), true, false, false, false );
			fplayers.addElement( player );		
			player = new FPlayer ( new Vector2d( -33.0, -20.0 ), true, false, false, false );
			fplayers.addElement( player );		

			// two midfielders
			player = new FPlayer ( new Vector2d( -17.0, 18.0 ), false, true, false, false );
			fplayers.addElement( player );		
			player = new FPlayer ( new Vector2d( -17.0, -18.0 ), false, true, false, false );
			fplayers.addElement( player );		

			// five forwards
			player = new FPlayer ( new Vector2d( -1.5, 26.0 ), false, false, true, false );
			fplayers.addElement( player );		
			player = new FPlayer ( new Vector2d( -10.0, 10.0 ), false, false, true, false );
			fplayers.addElement( player );		
			player = new FPlayer ( new Vector2d( -3.0, 0.0 ), false, false, true, true );	// kicker	
			fplayers.addElement( player );		
			player = new FPlayer ( new Vector2d( -10.0, -10.0 ), false, false, true, false );
			fplayers.addElement( player );		
			player = new FPlayer ( new Vector2d( -1.5, -26.0 ), false, false, true, false );
			fplayers.addElement( player );		
		}		
		else if ( formation == "343" )
		{
			// the goalie 
			player = new FPlayer ( new Vector2d( -48.0, 0.0 ), false, false, false, false );
			fplayers.addElement( player );		

			// three defenders
			player = new FPlayer ( new Vector2d( -33.0, 20.0 ), true, false, false, false );
			fplayers.addElement( player );		
			player = new FPlayer ( new Vector2d( -35.0, 0.0 ), true, false, false, false );
			fplayers.addElement( player );		
			player = new FPlayer ( new Vector2d( -33.0, -20.0 ), true, false, false, false );
			fplayers.addElement( player );		

			// four midfielders
			player = new FPlayer ( new Vector2d( -17.0, 20.0 ), false, true, false, false );
			fplayers.addElement( player );		
			player = new FPlayer ( new Vector2d( -22.0, 8.0 ), false, true, false, false );
			fplayers.addElement( player );		
			player = new FPlayer ( new Vector2d( -22.0, -8.0 ), false, true, false, false );
			fplayers.addElement( player );		
			player = new FPlayer ( new Vector2d( -17.0, -20.0 ), false, true, false, false );
			fplayers.addElement( player );
			
			// three forwards
			player = new FPlayer ( new Vector2d( -1.5, 18.0 ), false, false, true, false );
			fplayers.addElement( player );		
			player = new FPlayer ( new Vector2d( -1.5, 0.0 ), false, false, true, true );	// kicker
			fplayers.addElement( player );		
			player = new FPlayer ( new Vector2d( -1.5, -18.0 ), false, false, true, false ); 
			fplayers.addElement( player );		
		}		
			
		//System.out.println( "\n Set formation " + formation );	
	}
	

	public Vector2d getHome( int role )
	{
		FPlayer player = (FPlayer)fplayers.elementAt( role );
		return player.home;
	}
	
	public boolean isGoalie( int role ) 
	{
		return (role == 0);
	}

	public boolean isDefender( int role ) 
	{
		FPlayer player = (FPlayer)fplayers.elementAt( role );
		return player.defender;
	}
	
	public boolean isMidfielder( int role ) 
	{
		FPlayer player = (FPlayer)fplayers.elementAt( role );
		return player.midfielder;
	}

	public boolean isAttacker( int role ) 
	{
		FPlayer player = (FPlayer)fplayers.elementAt( role );
		return player.attacker;
	}

	public boolean isKicker( int role ) 
	{
		FPlayer player = (FPlayer)fplayers.elementAt( role );
		return player.kicker;
	}

	
	// instances of this class are stored in the Formation 
	private class FPlayer
	{
		Vector2d 	home; 	// player default position on the field  
		
		// these three categories could be treated differently in positioning
		boolean 	defender;
		boolean 	midfielder; 
		boolean 	attacker;
		
		boolean 	kicker;	// if true, the server would move this guy close to 
							// the ball before kickoff or corner kick.
							// if there is no kicker, server selects a default 
							// player itself, thus disturbing the formation

		FPlayer( Vector2d 	home, 
				 boolean 	defender, 
				 boolean 	midfielder, 
				 boolean 	attacker,
				 boolean 	kicker )
		{
			this.home 		= home;
			this.defender 	= defender;
			this.midfielder = midfielder;	
			this.attacker 	= attacker;
			this.kicker 	= kicker;
		}
		
	}
	
}