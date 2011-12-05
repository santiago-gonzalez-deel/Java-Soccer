/* AIPlayer.java
   This class implements a soccer player who is gets 
   sensing info, plans its moves and executes the plan.

   Copyright (C) 2001  Yu Zhang
   modified by Vadim Kyrylov (October 2004)

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 2 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the 
   Free Software Foundation, Inc., 
   59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * by Vadim Kyrylov
 * January 2006
 *
*/
// NOTE: all this application is better to view with the soccer agent' standapoint.
//       So you will find comments like "I player", "Should I do something", etc. 


package tos_teams.africa;

import soccer.common.*;

import java.util.*;
import java.io.*;
import java.net.*;

// This class implements a simulated soccer player.
// It receives information from the server about the state of the world 
// and generates  commands controlling this player. These commands
// are communicated back to the server that executes them by updating the
// state of the world.

// The player transforms all coordinates in the received data so that he 
// assumes that he is playing for the left-hand team. Therefore, the opponent  
// is always perveived to be on the right. 
// Before sending data back to the server, the player transforms all 
// coordinates as appropriate. As these transformations are trivial, they 
// do not create any noticeable overhead. The benefit is the significant 
// simplification of all algorithms dealing with geometry.

public class AIPlayer extends Thread 
{
	// ball kicking force constants
	// (used for convenience only)
	private static final int K_FORCE_SMALL 		= 15;
	private static final int K_FORCE_AUTOP_RAP 	= 20;
	private static final int K_FORCE_AUTOP_LEN	= 15;
	private static final int K_FORCE_MODERATE 	= 30;
	private static final int K_FORCE_MEDIUM	 	= 50;
	private static final int K_FORCE_MAXIMAL 	= 100;
	
	// player dash force constants
	// (used for convenience only)
	private static final int MV_FORCE_NOTHING 	=  0;  
	private static final int MV_FORCE_SMALL 	= 10;  
	private static final int MV_FORCE_MODERATE 	= 30;
	private static final int MV_FORCE_MEDIUM 	= 50;
	private static final int MV_FORCE_MAXIMAL 	= 100;
	
	// time interval in steps to print out the idling time statistics
	private static final int REPORT_STEPS_NUM = 5000;		
	
	// these objects are used for the communication with the server
	private Transceiver transceiver;
	private InetAddress myAddr = null;		// my address as a receiver
	private int			myPort; 			// my port as a receiver
	private DriveData 	aDriveData;
	private	KickData 	aKickData;
	
	// the state of the world as perceived by the player 
	private WorldModel 	aWorldModel;	
	private WorldData    aWorldData; 		// the visual info about the world
		
	private Formation   aFormation;			// my team formation
	private int			timeToGrabLeft = 0;

	// these two legacy variables are used for the debug print only;
	// consider using the Thread name instead
	private int 		playerNumber = 0;	
	private int 		playerTeamID = 0;	
		
	// my state	variables
	private char 		myside;   			// 'l' or 'r'
	private boolean 	amIGoalie;
	private boolean 	isGrabbedByMyself = false;
	private boolean 	teleportDone = false;
	
	private int 		bigInteger = 100;	// is used for calcultaing the ball movement
	private int			numOfPassDirections = 180;	// is used for passing
	
	// These variables are used for collecting statistics about lost packets; 
	// A lost packet is a lost opportunity for the agent to act in curent cycle. 
	// (Packes are lost if computations are too complex or 
	// the computer is too slow.)
	private static final int  MODULUS = 1000;
	private	int 		receivedPacketCount = 0;	
	private	int 		previousReceivedPacketID = -1;	
	private	int 		lostPacketCount = 0;
	private double 		lostPacketFactor = 0;

	// this variable is used for calculating the idle time of this thread
	private double		processingTime;
	
	public AIPlayer( 	Transceiver transceiver, 
				  		InitData initData, 
				  		char side, 
				  		int role,						// depricated
				  		Formation aFormation ) 
	{
		super("Player-" + (role + 1) + "-" + side );	// set the thread name
		System.out.print("Starting " + getName() );
		this.transceiver = transceiver;
		this.myside = side;	  	
		this.amIGoalie = ( role == 0 );
		if ( amIGoalie )
			System.out.println("  ** goalie **");
		else 
			System.out.println();
		this.aFormation = aFormation;
		this.aWorldModel = new WorldModel( transceiver, side, role );
	}
	
	
	// this method is what exactly I robotic player am doing:
	// getting info from sever, processing it, making decision, and sending
	// the commands back
	
	public void run() 
	{	
	    long count = 0;
	    processingTime = 0;
	    
		while(true)	// this infinite loop terminates with the application
		{
			try
			{				
				// I sense the world state 
				Packet receivedPacket = transceiver.receive();
	            
	            // get the time before the computations
	            long timeBefore = System.currentTimeMillis();
	            // idle time includes waiting for the packet to arrive 
	            // (most part of the time) and receiving it (a small fraction)

				// I update my perception of the state of the world 
				aWorldModel.updateAll( receivedPacket );
				aWorldData = aWorldModel.getWorldData(); 	
				
				// I plan my actions and save them to the World Model
				aWorldModel = plan4All( aWorldModel );
				
				// I execute actions by sending commands to server
				execute( aWorldModel );
				
				try {
					aWorldModel.setActionTime( aWorldData.time );					
				} catch( Exception e ) { 
					// this just protects from crashing 
					// before full connection with the server is established
					//System.out.println("Exception caught: " + e );
				}	
				
				// I do some housekeeping
				receivedPacketCount++;
				checkLostPackets( receivedPacket );
				/*
				if ( receivedPacketCount%100 == 0 )
					System.out.println("* packet " + receivedPacketCount 
							+ " " + getName() + "  is still alive");				
				try {
					sleep( 70 );		// this just slows down the agent to 
										// aSeeData what happens with packets
				} catch ( InterruptedException ie ) {}
				*/
	            
	            // get the time after the computations
	            count++;
	            long timeAfter = System.currentTimeMillis();
	            calcIdlingPercent( timeBefore, timeAfter, count );
			}
			catch( IOException ioe ) { }
		}
	} 

	

	// execute action by generating a commandPacket for the server
	private void execute( WorldModel world ) throws IOException
	{   
		switch( world.getActionType() )
		{
			case WorldModel.NOACTION:	; 					break; // do nothing
			case WorldModel.SHOOT: 	 	shootGoal(); 		break; // modified!
			case WorldModel.MOVE:  	 	moveTo(); 			break;
			case WorldModel.TURN:  	 	turn(); 			break;
			case WorldModel.PASS:  	 	passTo(); 			break;
			case WorldModel.CHASE: 	 	chaseBall(); 		break;
			case WorldModel.GRAB:  	 	grabBall();			break;
			case WorldModel.MOVEWBALL:	moveWithBall();		break;
			default: ;  
		}
	}
 

	/*******************************************
	 *
	 * player high-level decision making methods 
	 *
	 *******************************************/
	
	// this method determines my soccer player action 
	// and sets action type in the WorldModel, no matter what my role and
	// what the game situation are
	//
	private WorldModel plan4All( WorldModel world )
	{
		if ( world.getGamePeriod() == RefereeData.NO_GAME ) {
			// wait until the Soccer Server is ready to run the game
			world.setActionType( WorldModel.NOACTION );	
			return world;
	//<-----
		}
		
		/*if( world.amIOffside() ) { // 
			
			world.determineWhereToMoveOffSide();
			world.setActionType( WorldModel.MOVE );
			      
		} else*/
		if( amIGoalie ) {
			
			plan4goalie( world ); 	// plan goalie actions
			
		} else {
			// Soy jugador de campo
			
			if( world.isBallKickable() ) {
				
				// plan actions if I am with the ball
				kickBall( world );
				
			} else if ( world.amInearestTmmToBall() ) { 
				// plan actions if I do not control the ball but
				// can reposess the control
				// ( consider using amItheFastestToBall() )

				//System.out.println(aWorldData.getMyself().id + "-" myside
							//+ " I'm  nearest getBallPossession() = " 
							//+ world.getBallPossession() );

				if( world.isMyTeamOffside() || aWorldData.getBall().isGrabbed ) 
					world.setActionType( WorldModel.MOVE );
				else 
					world.setActionType( WorldModel.CHASE );
		    
		    } else {
		    	// as I am rather far away from the ball, I decide to move
		    	// and thus determine my destination
		    	// ( this is critical, as this what I am doing 90 per cent 
		    	//   of the time ) 
				world.determineWhereToMove(); //IMPORTANTE
				world.setActionType( WorldModel.MOVE );
			} 
		}
		
		/*		
		if ( world.getActionType() == WorldModel.MOVE )
			if ( playerTeamID*playerNumber == 6 ) {
				Vector2d mypos = aWorldData.getMyself().position;
				float disToMove = (float)mypos.distance( world.getDestination() );  
				System.out.println( aWorldData.getMyself().id + "-" myside 
								+ " Moving disToMove = " + disToMove );	      
		}	*/	
		
	    return world; 		// this returned value containd updated action variables
	    
  	} // plan4All
  	
  	
	// this (very basic, though) method plans actions for the goalie;
	// updates world.actionType
	//
	private WorldModel plan4goalie( WorldModel world )
	{
		if( world.isBallKickable() ) {
			
			// catch, or pass the ball, or move with it
			if ( shouldICatchBall( world ) ) {
				
				System.out.println(myside + " == Goalie caught the ball"); 
				timeToGrabLeft = WorldModel.MAX_GRABBED_STEPS; 
				world.setActionType( WorldModel.GRAB );
				isGrabbedByMyself = true;
			
			} else if ( isGrabbedByMyself ) {		// was world.isGrabbedByMyself
				
				moveWithBallOrKick( world );
				timeToGrabLeft--;
				if ( timeToGrabLeft <= 0 ) {
					isGrabbedByMyself = false;
					timeToGrabLeft = 0;
				}
			
			} else {
				kickBall( world );
			}
		} else {
			// figure out where the ball can be intercepted
			Vector2d interceptionPoint = getBallInterceptPsn();
			
			// possible improvement: using 'fastest to the ball'
			if ( ( world.amInearestTmmToBall() ) && 
				 world.isInOwnPenaltyArea( interceptionPoint ) ) {
				world.setActionType( WorldModel.CHASE );	
			} else { 
				world.determineWhereToMove();
				world.setActionType( WorldModel.MOVE );
			}
		}
		
		return world; 		
	}
	

	/*******************************************
	 *
	 * player low-level decision making methods 
	 *
	 *******************************************/

	// this method plans my actions with the ball.
	// it is very basic and do not distiguish between the goalie 
	// and field players (ideally, goalie behavior must be different)
	//
	private void kickBall( WorldModel world )
	{
		// the order of the cases below changes the player 
		// behavior substantially
		
		if( shouldIScore( world ) ) // modified!
		{
			world.setActionType( WorldModel.SHOOT );    	
		} 
		else if ( shouldIPass( world, numOfPassDirections ) )
			; // do nothing; all is done inside shouldIPass
			// (consider passing forward only here and
			// making passes back if only holding the ball is impossible)
		else if ( shouldIdribbleFast( world ) )
			; // do nothing; all is done inside shouldIdribbleFast
		else if ( shouldIdribbleSlow( world ) )
			; // do nothing; all is done inside shouldIdribbleSlow
		else if ( shouldIholdBall( world ) )
			; // do nothing; all is done inside shouldIholdBall
		else 
			// execute some last resort action like kicking the ball far away
			// ( could be completely removed if the above methods
			// are good enough )
			clearBall( world );
		
	} // kickBall

	
	// this method plans and executes my actions as the goalie 
	// while moving with the grabbed ball or decide to kick it
	private void moveWithBallOrKick( WorldModel world ) 
	{
		if ( canIMoveWithBall( world ) )
			world.setActionType( WorldModel.MOVEWBALL );
		else if ( shouldIPass( world, numOfPassDirections ) )
			; // do nothing; all is done inside shouldIPass 
		else 
			clearBall( world );			
	}

	
	// this method executes my actions as the goalie 
	// while moving with the grabbed ball.
	// I chose to move in the direction of the penalty area corner 
	// where there are fewer opponents 
	// this is not necessarily the best method, though
	private void moveWithBall() throws IOException
	{
		Vector2d mydestination;
		Vector2d centerTop, centerBot;
		
		centerTop = WorldModel.PENALTY_CORNER_L_T;
		centerBot = WorldModel.PENALTY_CORNER_L_B;
		
		int numOfOpponentsTop 
				= aWorldModel.countOpponentsInCircle( 	centerTop, 
														WorldModel.WIDTH ); 
																
		int numOfOpponentsBot 
				= aWorldModel.countOpponentsInCircle( 	centerBot, 
														WorldModel.WIDTH );
		if ( numOfOpponentsTop < numOfOpponentsBot )
			mydestination = centerTop;
		else
			mydestination = centerBot;
		 
		moveToPos(mydestination);
		
		//if ( timeToGrabLeft%5 == 0 )	// reduce packet sending frequency
		grabBall(); 				// keep teleporting the ball with myself
	}
	
	
	// this method returns true if I decide to shoot the goal.
	// this is just a placeholder; more sophisticated decision making is recommended.
	// for example, consider taking into accout the the goalie position and determining 
	// the scoring point in the goal accordingly 
	//
	private boolean shouldIScore( WorldModel world )
	{
		boolean should = false;
		double myX = aWorldData.getMyself().position.getX();	
		double dist = aWorldData.getMyself().position
											.distance(world.getOppGoal());
		if( dist < 20.0  && myX < 48.0 ) {	// menor que 15 y no sobre la linea, patear!
			should = true;
			System.out.println( aWorldData.getMyself().id + "-" + myside 
						+ " shooting at the goal, dist=" + (float)dist );
		}
		return should; 
	}
  

	// this method returns true if I, as the goalie, can move with 
	// the grabbed ball.
	// this is just a placeholder; more sophisticated decision making is recommended.
	// the only considerations here are:
	// (1) I must not be leaving the penalty area (with some margin) and 
	// (2) time permits to keep grabbing the ball (this delay allows 
	//     the teammates to move forward while I keep the ball)
	// These two rules are enforced by the soccer server (by modifying this 
	// method, you may want to check what their violation may result in).
	//
	private boolean canIMoveWithBall( WorldModel world )
	{
		if ( world.inPenaltyArea( aWorldData.getMyself().position, 
									-2.0 ) ) { 	// leave a 2 meter inside margin
			if ( timeToGrabLeft > 1 ) 			// leave 1 cycle 
				return true;
			else
				return false;
		} else {
			//if ( timeToGrabLeft > 0 ) 
				//System.out.println("Goalie is leaving the penalty area "
					//+ aWorldData.getMyself().position );
			
			timeToGrabLeft = 0;
			return false; 
		}
	}
	
	
	// this method returns true if I decide to dribble the ball so that 
	// I am passing it to myself and advance towards the oponent goal, or
	// wherevere the tactical gain could be increased.
	// this is just a placeholder; more sophisticated method is recommended.
	// for example, consider time balance between opponent players and myself 
	//
	private boolean shouldIdribbleFast( WorldModel world )
	{
		if( amIGoalie )
			return false;	// the goalie is not supposed to dribble !
		
		if ( world.countOpponentsInCircle( 	aWorldData.getMyself().position, 
											5.0 ) > 0 ) {		// magic number
			// there is at least one opponent player near me;
			// therefore, I should not dribble 
			return false;
		}		
		
		// in this version, I can dribble only towards 
		// the opponent goal, with some random deviation
		world.setActionType( WorldModel.PASS );			// passing to myself
		world.setKickForce( K_FORCE_AUTOP_RAP );
		double dir = aWorldData.getMyself().position
						.direction( world.getOppGoal() ) 
						+ (Math.random() - 0.5)*30.0;
		world.setKickDirection( dir );
		
		//if ( playerTeamID*playerNumber == 6 )
			System.out.println( aWorldData.getMyself().id + "-" + myside
				+ " Dribble in dir = " + (float)world.getKickDirection() );

		return true; 
	}

	// this method returns true if I decide to dribble the ball so that 
	// I could move with the ball while keeping it within the controll radius.
	// this is just a stub 
	//
	private boolean shouldIdribbleSlow( WorldModel world )
	{
		return false;
	}

	// this method returns true if I decide to stand with the ball and turning
	// about it so that closest opponent could not reach it and I could wait
	// until good passing situation develops or I could dribble the ball.
	// this is just a stub 
	//
	private boolean shouldIholdBall( WorldModel world )
	{
		return false;
	}


	// this method returns true if I decide to pass the ball to a teammate
	// I evaluate 'numOfDir' possible passing directions and 
	// select the best one, if any suitable direction exists.
	// if all 'numOfDir' options are poor, this method returns false
	// (### magic numbers everywhere; much could be improved ###)
	//
	private boolean shouldIPass( WorldModel world, int numOfDir )
	{
		Vector teammates = getTeammates ( world ); 
		Vector opponents = getOpponents ( world ); 
		double xx, yy;
		
		boolean should = false;	
		double min_risk = 100000;			// minimum is sought
		double best_pass_dir = 0;
		
		// I consider 'numOfDir' possible passing directions and 
		// select the least risky one, if possible
		for( int i=0; i<numOfDir; i++ )
		{
			double risk =0;
			double our_value = 0, their_value;
			
			// the possible passing direction
			double pass_dir = i * 360/numOfDir;
			pass_dir = Util.normal_dir( pass_dir );
			
			// don't kick it out of the pitch
			xx = aWorldData.getMyself().position.getX() 
						+ 10 * Math.cos( Util.Deg2Rad(pass_dir) );
			yy = aWorldData.getMyself().position.getY() 
						+ 15 * Math.sin( Util.Deg2Rad(pass_dir) );
			
			if( xx > WorldModel.LENGTH/2 || xx < -WorldModel.LENGTH/2 
										 || yy > WorldModel.WIDTH/2 || yy < -WorldModel.WIDTH/2 ) 
				risk = risk + 55500;
						
			// are my opponents in this direction? (greater is better)
			//System.out.println( "-- checking opponents in pass_dir = " + (float)pass_dir );
			their_value = getDirectionValueForTeam( opponents, pass_dir, false );
			
			if( their_value > 1900.0 )
			{
				// pass_dir is rather clear of the opponents
				// are my teammates in this direction? (smaller is better)
				//System.out.println( "-- checking teammates in pass_dir = " + (float)pass_dir );
				our_value = getDirectionValueForTeam( teammates, pass_dir, true );				
				risk = risk + our_value; 
				
				// passing the ball to the opponent side is better 
				risk = risk - 1000 * (-0.5 + ( 180 - Math.abs(pass_dir))/180 );
								
				if( risk < min_risk )
				{
					min_risk = risk;
					best_pass_dir = pass_dir;
					should = true;
				}
			}
			/*
			System.out.println( aWorldData.getMyself().id + "-" myside 
								+ " dir=" + (float)pass_dir  
								+ " risk = " + (float)risk 	
								+ " our_v = " + (float)our_value 
								+ " their_v = " + (float)their_value );
			*/
		}	
		
		if ( min_risk > 250)
			should = false;
			
		if ( should ) {
			world.setActionType( WorldModel.PASS );		
			// consider using force adjusted to distance
			//best_pass_dir
		/*	
			for ( int i = 0; i < teammates.size(); i++ )
			{
				Player player = (Player) teammates.elementAt( i );
				
				// I exclude myself
				if ( !player.equals( aWorldData.getMyself() ) ) {
					
					double plrdir = aWorldData.getMyself().position
												.direction( player.position );
					double ang = Util.normal_dir( plrdir - best_pass_dir );					
				
					if(Math.abs ( ang ) < 1.0) {
						double dist = aWorldData.getMyself().position.distance(player.position);
				
						min_dist = dist < min_dist ? dist : min_dist;
					}
				}
			}
			
			double force = min_dist > 25 ? 100 : min_dist * 4;
			
			world.setKickForce(force);
			//*/world.setKickForce(K_FORCE_MAXIMAL);
			world.setKickDirection( best_pass_dir );
			
			//if ( playerTeamID*playerNumber == 6 )
				System.out.println( aWorldData.getMyself().id + "-" + myside 
							+ " PASS min_risk=" + (float)min_risk 
							+ " dir=" + (float)best_pass_dir 
							+ " force=" + (float)world.getKickForce() );
		  
		}
		
		return should; 
	}

	
	// this very basic method implements my decision as a goalie 
	// on whether to catch and grab the ball now.
	// further elaboration is recommended
	
	private boolean shouldICatchBall( WorldModel world ) 
	{
		// I do not catch the ball that is moving from the goal
		if ( Math.abs( Vector2d.polar_dir( world.getBallVelocity() ) ) < 90 ) 
			return false;
		else {
			// I decide to catch the ball only if too many
			// opponents are hanging around 
			
			if ( !isGrabbedByMyself ) {
				
				Vector2d myPosition = aWorldData.getMyself().position;
				int numOfOpponentsClose 
						= world.countOpponentsInCircle( myPosition, 20.0 ); 
				int numOfOpponentsTooClose 
						= world.countOpponentsInCircle( myPosition, 10.0 );
				
				//System.out.println("Goalie Close = " + numOfOpponentsClose 
						//+ " TooClose = " + numOfOpponentsTooClose );
		
				return ( numOfOpponentsClose > 1 || 
						 numOfOpponentsTooClose > 0 ); 
			} else {
				return false;	// I do not need to catch the ball, as I have grabbed it !
			}
		}
	}
	
	/*******************************************
	 *
	 * player action execution methods 
	 *
	 *******************************************/
  
  
	// this method executes kicking the ball in the opponent goal center
	// (much could be improved here; center is not always the best target)
	private void shootGoal() throws IOException
	{
		Vector<Player> ellos = aWorldData.getTheirTeam();
		
		Player arquero = ellos.elementAt(0);
		
		double y = arquero.position.getY();

		double dir;
		
		Vector2d paloArriba = new Vector2d(WorldModel.LENGTH/2, 3);
		Vector2d paloAbajo = new Vector2d(WorldModel.LENGTH/2, -3);
		
		if(y == 0.0) { //Arquero en el medio, patear a nuestro palo
			
			double ourY = aWorldData.getMyself().position.getY();
			
			if(ourY <= 0.0) {
				dir = aWorldData.getMyself().position
						.direction( paloAbajo );			
			}else {
				dir = aWorldData.getMyself().position
						.direction( paloArriba );
	
			}
		} else if (y > 0.0) { // arquero arriba, patear abajo
			dir = aWorldData.getMyself().position
					.direction( paloAbajo );
			
		} else { // arquero abajo, patear arriba
			dir = aWorldData.getMyself().position
					.direction( paloArriba );
			
		}
		
		sendKickPacket( dir, MV_FORCE_MAXIMAL ); 
	}
	
	// this method executes kicking the ball to a teammate  
	// (hardly anything could be improved here)
	private void passTo() throws IOException
	{
		sendKickPacket( aWorldModel.getKickDirection(), 
							aWorldModel.getKickForce() ); 
	}

	// this method sends the kick packe the othe server
	// it is recommanded to use just one method, as it also resents some
	// class variables 
	private void sendKickPacket(double direction, double force) throws IOException
	{
		// reset variables (unless this is done, the game may get stuck)
		isGrabbedByMyself = false;
		timeToGrabLeft = 0;
		
		aKickData = new KickData( direction, force );
		Packet commandPacket = new Packet( 
									Packet.KICK, 
									aKickData, 
									Africa_Team.address, 
									Africa_Team.port );
		aWorldData.send( commandPacket );
	}


	// this method executes grabbing the ball by the goalie 
	// player coodinates are sent to the server so that it 
	// assigned them to the ball; as the player is moving, the ball
	// is dragged with the player.
	// (hardly anything could be immproved here)
	
	private void grabBall() throws IOException
	{
		Vector2d position = aWorldData.getMyself().position;
		TeleportData aTeleportData = new TeleportData( TeleportData.GRAB, 
														myside, position );
		Packet commandPacket = new Packet( 
									Packet.TELEPORT, 	// TELEPORT action is only allowed for the goalie and
														// for any player in the 'before kick off' state;
														// (rules are enforced by the server)
									aTeleportData, 
									Africa_Team.address, 
									Africa_Team.port );
		aWorldData.send( commandPacket );
	}
  	

	// this method forces the soccer server to move me into
	// my home position without delay
	private void teleportMyself( WorldModel world ) 
	{		
		TeleportData aTeleportData = 
			new TeleportData( 	myside, 
								playerNumber,		
								world.getHomePos().getX(), 
								world.getHomePos().getY() );
		
		Packet commandPacket = 
			new Packet( Packet.TELEPORT, 	// TELEPORT action is only allowed for the goalie and
											// for any player in the 'before kick off' state;
											// (rules are enforced by the server)
						aTeleportData, 
						Africa_Team.address, 
						Africa_Team.port );
		try {
			aWorldData.send( commandPacket );
		} catch ( IOException e ) {
				System.out.println("teleportMyself() " + e ); 
		}		
	}

  	// this method determines the direction where 
	// I should kick the ball as the last resort (clearing ball)
	// this is just a placeholder; more sophisticated rule is recommended.
	// one possibity is completely removing this action.
	//
	private void clearBall( WorldModel world )
	{	
		world.setActionType( WorldModel.SHOOT );
		world.setKickForce ( K_FORCE_MAXIMAL );
		world.setKickDirection( aWorldData.getMyself().position
										.direction( world.getOppGoal() ) );
		
		//if ( playerTeamID*playerNumber == 6 )
			System.out.println( aWorldData.getMyself().id + "-" + myside 
				+ " Clear ball in dir = " + (float)world.getKickDirection() );
	}

	
	// this method plans and executes my actions to chase the ball
	// my resulting trajectory is not necessarily optimal, though
	private void chaseBall() throws IOException
	{
		double direction2Ball = aWorldData.getMyself()
									.position.direction( getBallInterceptPsn() );
																		
		aDriveData = new DriveData( direction2Ball, getForce() );
		Packet commandPacket = new Packet( 
									Packet.DRIVE, 
									aDriveData, 
									Africa_Team.address, 
									Africa_Team.port );
		aWorldData.send( commandPacket );
	}

	
	// this method plans and executes my actions to move on the field
	// without the ball 
	private void moveTo() throws IOException
	{
		double distance = aWorldData.getMyself().position
								.distance( aWorldModel.getDestination() );
		double direction = aWorldData.getMyself().position
								.direction( aWorldModel.getDestination() );
		
		//if ( playerTeamID*playerNumber == 6 ) 
		//System.out.println( aWorldData.getMyself().id + "-" myside
		//+ " distance = " + (float)distance + " direction = " + (float)direction);		

		double force;
		
		if( distance >= 5.0 ) 
			force = MV_FORCE_MAXIMAL;
		else if ( aWorldData.getBall().isGrabbed )
			// do not bump over the goalie who has grabbed the ball
			// ( actually, the server also enforces this rule )
			force = MV_FORCE_SMALL/3.0; 	
		else
			force = MV_FORCE_SMALL;
		
		aDriveData = new DriveData( direction, force );
		Packet commandPacket = new Packet( 
									Packet.DRIVE, 
									aDriveData, 
									Africa_Team.address, 
									Africa_Team.port );
		aWorldData.send( commandPacket );
	}

	
	// this method executes my actions to move to given position
	private void moveToPos( Vector2d position ) throws IOException
	{
		double distance = aWorldData.getMyself().position
								.distance( position );
		double direction = aWorldData.getMyself().position
								.direction( position );
				
		aDriveData = new DriveData( direction, MV_FORCE_MAXIMAL );
		Packet commandPacket = new Packet( 
									Packet.DRIVE, 
									aDriveData, 
									Africa_Team.address, 
									Africa_Team.port );
		aWorldData.send( commandPacket );
	}

	
	// this method executes my actions to turn in the direction
	// to the 'facing position' which is set in the world model
	private void turn() throws IOException
	{
		double direction = aWorldData.getMyself().position
								.direction( aWorldModel.getFacingPos() );
				
		// in the server, a drive with zero force just results in a turn
		aDriveData = new DriveData( direction, MV_FORCE_NOTHING );
		Packet commandPacket = new Packet( 
									Packet.DRIVE, 
									aDriveData, 
									Africa_Team.address, 
									Africa_Team.port );
		aWorldData.send( commandPacket );
		
		//System.out.println( aWorldData.getMyself().id + "-" myside
						//+ " Turning in direction = " + (float)direction );	      
	}
	

	// this methods presumably allows me avoiding Offside, if any
	private void avoidOffside() throws IOException
	{
		double direction = aWorldData.getMyself()
							.position.direction( aWorldModel.getDestination() );
		aDriveData = new DriveData(direction, MV_FORCE_MAXIMAL);
		Packet commandPacket = new Packet( 
									Packet.DRIVE, 
									aDriveData, 
									Africa_Team.address, 
									Africa_Team.port);
		aWorldData.send(commandPacket);
	}

	
	/*******************************************
	 *
	 * low-level computational methods 
	 *
	 *******************************************/
	
	// this method evaluates passing direction 'dir' with respect to 
	// a team of 'players' 
	// returns a heuristic estimate of the goodness of this direction.
	// this estimate is greater if the players are far away from the passing direction;
	// it is equal zero if there is a player very close to this direction
	//
	private double getDirectionValueForTeam( Vector players, double dir, boolean myTeam)
	{
		int best_time 		= 200, 
		    mean_time 		= 0;
		int player_count 	= 0;
		
		for ( int i = 0; i < players.size(); i++ )
		{
			int time = 0;
			Player player = (Player) players.elementAt( i );
			
			// I exclude myself
			if ( !player.equals( aWorldData.getMyself() ) ) {
				
				double plrdir = aWorldData.getMyself().position
											.direction( player.position );
				double ang = Util.normal_dir( plrdir - dir );
				
				double cota = myTeam ? 1.0 : 12.0;
				if( Math.abs( ang ) < cota )
				{
					if( aWorldData.getMyself().position.distance( player.position ) < 9.0 )
					{ // there is a nearby player in this direction
						mean_time = 0;
						best_time = 0;
						player_count = 1;
						break;
					}
					else if( aWorldData.getMyself().position.distance( player.position ) < 40 )
					{   
						Vector2d ballSpeed = Vector2d.polar(WorldModel.BALLMAXSPEED, dir);
						ballSpeed = ballSpeed.timesV( WorldModel.SIM_STEP_SECONDS );
						time = getInterceptTime( ballSpeed, 
											  player.position, new Vector2d() );
						player_count++;
						if( time < best_time )
							best_time = time;
						mean_time += time;
					}
				}
				//System.out.println("id=" + player.id + "-" + player.side 
					//+ " dir=" + (float)dir + " plrdir=" + (float)plrdir 
					//+ " ang=" + (float)ang + " time=" + time );
			}
		}
		
		if( player_count == 0 ) 
			mean_time = 200;
		else 
			mean_time = mean_time / player_count;	// decreaase if more than one player 

		return  mean_time * 2 + best_time * 10;			
	}
	
  
	// predict ball's stop position
	private Vector2d getBallStopPsn()
	{
		Vector2d ballPos = new Vector2d( aWorldData.getBall().position );
		Vector2d ballVel = new Vector2d( aWorldModel.getBallVelocity() );
		double ballSpeed = ballVel.norm();
		while(ballSpeed > 0.1)
		{
			ballPos.add(ballVel);
			ballVel.times(1 - WorldModel.FRICTIONFACTOR);
			ballSpeed = ballVel.norm();
		}
		return ballPos;
	}
	

	// predict number of cycles the player needs to intercept the ball
	// velocities are measured in meters per simulation step
	//
	private int getInterceptTime( Vector2d initBallVel, 
					              Vector2d playerPos, 
					              Vector2d playerVel )
	{
		double force;
		int maxTime = 150;
		
		if( aWorldModel.getDistance2Ball() >= 3 ) 
			force = MV_FORCE_MAXIMAL;
		else 
			force = MV_FORCE_MEDIUM;
		
		Vector2d ballPos = new Vector2d( aWorldData.getBall().position );
		Vector2d ballVel = new Vector2d( initBallVel );
		
		Vector2d myPos = new Vector2d( playerPos );
		Vector2d myVel = new Vector2d( playerVel );
		Vector2d myAcc = new Vector2d();
		
		double dir2Ball;
		
		for( int i=0; i < maxTime; i++ )
		{
			ballPos.add( ballVel );
			ballVel.times( 1.0 - WorldModel.FRICTIONFACTOR );
			
			dir2Ball = myPos.direction( ballPos );
			
			myPos.add(myVel);
			myVel.add(myAcc);
			myAcc.setX( force * Math.cos( Util.Deg2Rad(dir2Ball) ) * WorldModel.K1
			                 - myVel.getX() * WorldModel.K2 );
			                 
			myAcc.setY( force * Math.sin( Util.Deg2Rad(dir2Ball) ) * WorldModel.K1
			                 - myVel.getY() * WorldModel.K2 );
			
			if ( myPos.distance(ballPos) < WorldModel.CONTROLRANGE ) 
				return i;				 
		
			//System.out.println("-- i=" + i + " myPos=" + myPos + " ballPos=" + ballPos );
		}
		return maxTime;	 
	}

  
  	// determine the force necessary for for chasing the ball
	private double getForce() 
	{
		double force;
		if( aWorldModel.getDistance2Ball() >= 3.0 ) 
			force = MV_FORCE_MAXIMAL;
		else 
			force = MV_FORCE_MEDIUM;
			
		return force;
	}

	// predict the best ball interception point 
	// this method could be significantly improved (e.g. with respect to 
	// the presence of opponent players)
	//
	private Vector2d getBallInterceptPsn()
	{
		Vector2d ballPos = new Vector2d( aWorldData.getBall().position );
		Vector2d ballVel = new Vector2d( aWorldModel.getBallVelocity() );
		
		Vector2d myPos = new Vector2d( aWorldData.getMyself().position );
		Vector2d myVel = new Vector2d( aWorldModel.getMyVelocity() );
		
		// caluclate my acceleration
		Vector2d myAcc = new Vector2d();
		double force = getForce();
			
		myAcc.setX(force * Math.cos(Util.Deg2Rad(aWorldData.getMyself().direction)) * WorldModel.K1
		                    - myVel.getX() * WorldModel.K2);
		                     
		myAcc.setY(force * Math.sin(Util.Deg2Rad(aWorldData.getMyself().direction)) * WorldModel.K1
		                    - myVel.getY() * WorldModel.K2);
		double dir2Ball;
		
		for ( int i=0; i < bigInteger; i++ )
		{
			ballPos.add(ballVel);	// predicted ball position
			ballVel.times(1 - WorldModel.FRICTIONFACTOR);
			
			dir2Ball = myPos.direction(ballPos);
			
			myPos.add(myVel);
			myVel.add(myAcc);
			myAcc.setX(force * Math.cos(Util.Deg2Rad(dir2Ball)) * WorldModel.K1
			                     - myVel.getX() * WorldModel.K2);
			                     
			myAcc.setY(force * Math.sin(Util.Deg2Rad(dir2Ball)) * WorldModel.K1
			                     - myVel.getY() * WorldModel.K2);
			
			if ( myPos.distance(ballPos ) < WorldModel.CONTROLRANGE ) 
				return ballPos;				 
		}
		return getBallStopPsn();
	}


	/*******************************************
	 *
	 * 			householding methods
	 *
	 *******************************************/

	// this method collects lost packet statistics that could be 
	// useful for determining whether the client is running too slow.
	// (* a tradeoff exisits between the sophistication of algorithms and the 
	// time required to execute them; with too long time, player performance  
	// tends to deteriorate. this is a platform-dependent effect, though. *)
	// for example, on a slow computer, a substantial increase in the number  
	// of options the player evaluates when making decisions about passing 
	// or dribbling the ball may lead to a noticeable packet loss rate.
	
	private void checkLostPackets( Packet aPacket ) 
	{
		if( aPacket.packetType == Packet.SEE ) {
			// as the the SEE packets are sent on each simulation step 
			// and the server inserts in them the step ID, this parameter
			// can be used to detect packets losses 

			SeeData aSeeData = (SeeData)aPacket.data;
			
			if ( previousReceivedPacketID < 0 ) {
				// skip the first packet
			} else {
				
				
				int delta = aSeeData.time  
						- ( previousReceivedPacketID + 1 );
				if ( delta <0 )
					delta = MODULUS - delta; 
	
				if ( delta > MODULUS/2 )
					delta = 0; 	// just ignore too big losses
				
				lostPacketCount = lostPacketCount + delta;
				
				// this is the exponential smoothening method
				double weight = 0.5;			// a magic number
				lostPacketFactor = weight * delta + 
									(1 - weight) * lostPacketFactor;
				
				if ( lostPacketFactor > 2.0 ) {
					// print a warning that packets are being lost
					System.out.println("** " + getName() + " lost " + delta 
						+ " packets" + "  lostPacketFactor = " 
						+ ((int)(1000.0*lostPacketFactor))/1000.0 + "  **" );	
				}
			}
			previousReceivedPacketID = aSeeData.time;
		}	
	}


	// this method estimates the percentage of idling time and prints it out
	// with regular intervals; this estimate gives the idea of how close 
	// the process time to its limit is; it is based on some assumptions.
	// idling below 20% should be regarded insufficient.
	// its negative value means severe time deficit.
	//	
	private void calcIdlingPercent( long before, long after, long count )
	{
		// assumptions: 
		// (1) there are 22 player threads running;
		// (2) 50% of the total time is consumed by everything else, 
		//     including the OS, server, monitor, and other applications;
		//     (just a guess)
		
		double compTimeMilliseconds = (double)(after - before);
		processingTime = processingTime + compTimeMilliseconds;
		
		if ( count%REPORT_STEPS_NUM == 0 ) {
			
			processingTime = processingTime/REPORT_STEPS_NUM; 
			double idleTime 
						= 0.5 * (WorldModel.SIM_STEP_SECONDS*1000.0)/22.0 
								- processingTime;
			
			double idlingPercent 
						= 100.0*idleTime/(idleTime + processingTime); 
			double lostPercent 
						= 100.0*lostPacketCount/REPORT_STEPS_NUM; 
			
			System.out.println("\n@@@  " + getName() + 
					":\n proc time " + (float)processingTime 
					+ " ms, idling " 
					+ ((int)(1000.0*idlingPercent))/1000.0 +  "%"
					+ ", lost packets "  
					+ ((int)(1000.0*lostPercent))/1000.0 +  "%");
			processingTime = 0;
			lostPacketCount = 0;
		}
	}

	/****************************************************
	 *
	 * public get/set access methods for class variables
	 *
	 ****************************************************/
  
	public WorldModel getWorldModel()
	{
		return aWorldModel;
	}
	
  	public void setPlayerNumber( int number )
	{
		playerNumber = number;
	}
	
	public int getPlayerNumber() 
	{
		return playerNumber; 
	} 
	
 	public void setPlayerTeamID( int id )
	{
		playerTeamID = id; 
	}
	
	public int getPlayerTeamID() 
	{
		return playerTeamID; 
	} 

	private Vector getTeammates( WorldModel world )
	{
		return aWorldData.getMyTeam();
	}

	private Vector getOpponents( WorldModel world )
	{
		return aWorldData.getTheirTeam();
	}

}
