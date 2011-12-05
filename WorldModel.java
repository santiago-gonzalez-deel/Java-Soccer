/* WorldModel.java
   The soccer game world as percieved by the AI player. 

   Copyright (C) 2001  Yu Zhang
   (with modifications by Vadim Kyrylov; October 2004) 
 *
 * by Vadim Kyrylov
 * January 2006
 *
*/
// NOTE: all this application is better to view with the soccer agent' standapoint.
//       So you will find comments like "I player", "Should I do something", etc. 

package tos_teams.africa;

import soccer.common.*;
import java.io.*;
import java.util.*;


// this class maintains what I player perceive about the world and 
// my own state.
// All coordinates are transformed so that I beleieve that my team is 
// playeing on the left side. 

public class WorldModel 
{
	//==========  public static constants  ====================

	// Field data (in meters)
	public static int LENGTH           = 100;
	public static int WIDTH            =  65;
	public static int SIDEWALK         =   5;
	public static int RADIUS           =   9;
	public static int GOAL_DEPTH       =   2;
	public static int GOAL_WIDTH       =   8;
	public static int GOALAREA_WIDTH   =  18;
	public static int GOALAREA_DEPTH   =   6;
	public static int PENALTY_WIDTH    =  40;
	public static int PENALTY_DEPTH    =  16;  
	public static int PENALTY_CENTER   =  12;
	public static int CORNER           =   1; 

	// peanly area corners
	public static Vector2d 	// left top
		PENALTY_CORNER_L_T = new Vector2d(	-LENGTH/2 + PENALTY_DEPTH,
											PENALTY_WIDTH/2 );
	public static Vector2d 	// left botttom 
		PENALTY_CORNER_L_B = new Vector2d(	-LENGTH/2 + PENALTY_DEPTH,
											-PENALTY_WIDTH/2 );
	public static Vector2d 	// right top 
		PENALTY_CORNER_R_T = new Vector2d(	LENGTH/2 - PENALTY_DEPTH,
											PENALTY_WIDTH/2 );
	public static Vector2d 	// right botttom  
		PENALTY_CORNER_R_B = new Vector2d(	LENGTH/2 - PENALTY_DEPTH,
											-PENALTY_WIDTH/2 );	
	
	// High Level Actions Codes
	public static final int NOACTION   = 0;		// do nothing
	public static final int SHOOT      = 1;		// shoot the goal 
	public static final int MOVE       = 2;		// move to wherever is good for the team 
	public static final int PASS       = 3;		// pass the ball to teammate or myself
	public static final int CHASE      = 4;		// chase the ball
	public static final int UNSTUCK	   = 5;		// resolve a 'got stuck' situation
	public static final int OFFSIDE    = 6;		// move to whereever rules permit
	public static final int GRAB	   = 7;		// grab the ball (for goalie) 
	public static final int MOVEWBALL  = 8;		// move with the grabed the ball (for goalie)
	public static final int TELEPORT   = 9;		// teleport myself in home position
	public static final int TURN	   = 10;	// turn to 'facingPos'
	
	// Once the player makes up his mind to do an action, the action will
	// be carried out for next INERTIA steps. So this means, the client
	// does not need to send a command every step, he only needs to send
	// a command every INERTIA steps.
	// This will also give the client more time to think/calculate before 
	// actually doing something. This may be especially important when 
	// the computer is too slow and the payer have no enough time to complete
	// computations in just one simulation step.
	
	// ( consider experimenting with this parameter if time is insufficient )
	// INERTIA=1 leaves no room for the player to deliberate on its actions.
	public static final int 		INERTIA   =  1;  	
	
	// physical factors of the soccer world (if they differ from the server
	// settings, my calculations would be wrong)
	
	// simulation step duration in seconds
	public static final double 	SIM_STEP_SECONDS    = 0.05;
	
	// player will have chance to get the ball when ball-player 
	// distance is under this control range
	public static final double 	CONTROLRANGE   = 1.5; 
	
	// maximum ball speed in m/s
	public static final double 	BALLMAXSPEED   = 23;
	
	// ball friction factor, such as a1 = -FRICTIONFACTOR * v0;
	public static final double 	FRICTIONFACTOR     = 0.065;
	
	// player maximum speed (in m/s)
	public static final double 	MAXSPEED   = 7.5; 
	
	// the time a player needs to reach full speed (in sec)
	// without friction
	public static final double 	TIMETOMAX  = 1;
	
	// player's maximum dash force 
	public static final double 	MAXDASH  = 100;
	
	// player's maximum kick force 
	public static final double 	MAXKICK  = 100;
	
	// max dribble force factor, when player is dribbling, 
	//the max force he can use to dash is MAXDASH * DRIBBLE 
	public static  final double 	DRIBBLEFACTOR  = 0.4;
	
	// players collides when their distance is smaller than COLLIDERANGE
	public static final double 	COLLIDERANGE = 0.6;
	
	// K1 is the force factor, MAXSPEED speed divided by TIMETOMAX
	public static final double 	K1 = MAXSPEED * SIM_STEP_SECONDS * 
	                    			SIM_STEP_SECONDS / TIMETOMAX / MAXDASH;
	
	// K2 is the player running friction factor,
	public static final double 	K2 = MAXDASH / (MAXSPEED * SIM_STEP_SECONDS)*K1; 		
	
	// BK1 is the kick force factor 
	public static final double 	BK1 = BALLMAXSPEED * SIM_STEP_SECONDS / MAXKICK; 
	
    // number of cycles allowed keeping the ball grabbed by the goalie 
    public static int MAX_GRABBED_STEPS = 70;	// can be overriden by server

	// ball state constants 
	public static final int 	OUR_BALL = -1;
	public static final int 	NEUTRAL_BALL = 0;
	public static final int 	THEIR_BALL = 1;

	
	//==========  private members  ====================
	
	private WorldData    aWorldData; 		// the visual info about the world
	private Transceiver transceiver;
	private Formation 	aFormation; 
	
	// game state info as perceived by the player 
	private int 		gameMode = 0;	  
	private int 		gamePeriod = 0;  
	private char		sideToContinue = '?'; // who should continue the game 

	// variables used for deciding about ball possession
	private int 		whoseBallIs = NEUTRAL_BALL; 
	private double 		ballPossWeight = 0.15; 	// ball possession weight for smoothening possession indicator
	private double 		ballPossIndicator = 0;  // ball possession indicator
	private double 		ballPossThreshold = 0.7;  // decisionmaking threshold for ball possession 

	// variables used to store ball position at previous step,
	// needed for calculating ball velocity.
	private Vector2d 	ballPosition =  new Vector2d(); 
	private Vector2d 	ballVelocity = new Vector2d();

	// other ball state attributes
	private boolean 	isBallKickable = false;	
	private double 		distance2Ball;
	private double 		direction2Ball;  
	private Player 		nearestPlayerToBall;  
	private double 		minPlayerDistance;	
	private Player 		nearestTmmToBall; 
	private double 		minTmmDistance;	
	private Player 		nearestOppToBall; 
	private double 		minOppDistance;	

	// agent role information
	private int 		role; 			// if role == 0, I am the goalie *** depricated ***
	private char 		myside;			// my team's side ('l' or 'r') 
	
	// this is used for debug print only *** depricated ***
	private int 		playerNumber = 0;	
	private int 		playerTeamID = 0;	
	
	private Vector2d 	offensePos;		// my position in defense
	private Vector2d 	defensePos;		// my position in defense
	private Vector2d 	homePos; 		// my home position 	

	
	// used to store my position at previous step,
	// for calculating my velocity.
	private Vector2d 	prePosition = new Vector2d();
	private Vector2d 	myVelocity = new Vector2d();
	
	// my action parameters 
	private int 		actionType;
	private double 		dashForce;
	private double 		dashDirection;
	private double 		kickForce;
	private double 		kickDirection;
	private int 		actionTime = 0;
		
	// other player attributes
	private Vector2d 	destination = new Vector2d(); 	// my intended destination point
	private Vector2d	facingPos = new Vector2d();		// my intended position to face to
	private boolean 	iAmNearestToBall = false;
	private boolean 	iAmNearestTmmToBall = false;
	private boolean 	iAmOffside = false;
	private boolean 	myTeamIsOffside = false;
	
	private int			offsideSteps = 0; 	 // step counter to keep moving from offside
	private int 		kickOffTime = 0;	 // time elapsed after last kickoff
	
	private Vector2d 	oppGoal;		// opponent goal center
	private Vector2d 	ownGoal;		// own      goal center
	
	
	public WorldModel( Transceiver transceiver, char side, int role )
	{
		this.transceiver = transceiver;
		this.myside = side;
		this.role = role;
	
		oppGoal = new Vector2d(WorldModel.LENGTH/2, 0);
		ownGoal = new Vector2d(-WorldModel.LENGTH/2, 0);
	}
    
  
 	/*******************************************
	 *
	 *  world model update methods
	 *
	 *******************************************/
 
	// This method uses information about the new state of the world 
	// in the received packet and updates my world model;
	//	@param 'receivedPacket' is the message from the server
	
	public void updateAll( Packet receivedPacket )  throws IOException
	{		
		if ( receivedPacket.packetType == Packet.REFEREE ) {
			
			// process game control data and determine the game state
			
			RefereeData aRefereeData = (RefereeData)receivedPacket.data;	
			
			// set the game state as decided by the referee:
			gamePeriod 	= aRefereeData.period;
			gameMode 	= aRefereeData.mode; 	
			// team side who continues the game after the interruption 
			sideToContinue = aRefereeData.sideToContinue;			
			/*
			if ( playerTeamID*playerNumber == 6 ) {
				System.out.println( aWorldData.getMyself().id + "-" + myside
				+ " received Packet.REFEREE "  
        		+ " period = " + RefereeData.periods[gamePeriod] 
        			+ " mode = " + RefereeData.modes[gameMode] );
        	} */
					
		}		  
		else if( receivedPacket.packetType == Packet.SEE ) {
			
			// process visual data
			
			//if ( playerTeamID*playerNumber == 6 ) 
				//System.out.println( aWorldData.getMyself().id + "-" + myside
				//+ " received Packet.SEE ");  

			SeeData aSeeData = (SeeData)receivedPacket.data;
			
			// convert coordinates so that I was perceiving everything 
			// like my team is playing on the left-hand side
			aWorldData = new WorldData( aSeeData, myside, transceiver ); 
			
			//System.out.println( aWorldData.getMyself().id + "-" + myside
						//+ " aWorldData.getMyTeam().size()=" + aWorldData.getMyTeam().size()  
						//+ " aWorldData.getTheirTeam().size()=" + aWorldData.getTheirTeam().size() ); 
			

			// do I conrol the ball?
			isBallKickable = canIkickBall();
			
			// check for Offside (this info may be in received SEE packet)
			checkOffsideState();
			
			determineNearestPlayerToBall();
			
			// am I nearest to ball?
			iAmNearestToBall 
					= nearestPlayerToBall.equals( aWorldData.getMyself() );
			// am I nearest in my team?
			iAmNearestTmmToBall  
					= nearestTmmToBall.equals( aWorldData.getMyself() );
			
			/*
			if ( iAmNearestToBall )	
				System.out.println( aWorldData.getMyself().id + "-" + myside
							+ " I'm nearest: " + nearestPlayerToBall.id 
							+ " " + nearestPlayerToBall.side 
							+ " minTmmDistance=" + (float)minTmmDistance 
							+ " minOppDistance=" + (float)minOppDistance ); 
			*/
			// check for defense/attack situation
			determineBallPossession();
			
			// estimate ball velocity
			Vector2d.subtract(aWorldData.getBall().position, 
											ballPosition, ballVelocity);
			ballPosition.setXY(aWorldData.getBall().position);      
			
			// estimate my own velocity
			Vector2d.subtract(aWorldData.getMyself().position, 
											prePosition, myVelocity);
			prePosition.setXY(aWorldData.getMyself().position);
		}
	
	} // updateAll
  

	// This method determines whether I am controlling the ball
	private boolean canIkickBall()
	{				
		if( aWorldData.getBall().controllerType == aWorldData.getMyself().side &&  // my side
		     aWorldData.getBall().controllerId == aWorldData.getMyself().id ) 	 // my id
		     // server confirms that I am the ball controller
			return true;
		else 
			return false;
		
	} 
		
  
	// this method assigns two boolean variables, iAmOffside and 
	// myTeamIsOffside based on whether the server has 
	// detected offside or not
	private void checkOffsideState()
	{
		myTeamIsOffside = false;
		
		// find out if it's Offside
		if( iAmOffside )
		{
			if( offsideSteps == 0 ) 
				iAmOffside = false;
			else 
				offsideSteps--;
		} else {
			if( aWorldData.status == SeeData.NO_OFFSIDE ) {
				iAmOffside = false;
				myTeamIsOffside = false;
			}
			else if( aWorldData.status == SeeData.OFFSIDE ) {
				iAmOffside = true;
				myTeamIsOffside = true;
				offsideSteps = 30;
			} else if( aWorldData.status == SeeData.T_OFFSIDE ) {
				iAmOffside = false;
				myTeamIsOffside = true;
			}	      
		}		
	}
	
	
	// This method decides which team possesses the ball based on 
	// class variable 'nearestPlayerToBall'; it assigns class
	// variable 'whoseBallIs'. 
	// Decisioins made in several cycles are smoothed using  
	// exponential filtering
	private void determineBallPossession()
	{
		if ( gameMode == RefereeData.PLAY_ON ) {
			
			// make ball possession decision in current cycle
			double delta = 0;	
			if ( minPlayerDistance < 10.0 ) {		// magic number
				// find out if it's the offensive or defensive state of the game 
				if( nearestPlayerToBall.side == myside ) 
					delta = -1;		// 'our' ball
				else 
					delta =  1;		// 'their ball	
			
			} else {
				ballPossIndicator = 0;	
				delta = 0; 		// neutral
			}
					
			// do exponential filtering of -1, 0, +1 'delta' sequence over time.
			// if ballPossWeight=1, no filtering is done;
			// if ballPossWeight=0, value from the previous step is just reused
			
			ballPossIndicator = ballPossWeight*delta 
						+ ( 1 - ballPossWeight )*ballPossIndicator; 		
			
			// *** this randomness is inroduced only for better-looking demo;
			// *** remove it for using this program for serious purposes 
			ballPossIndicator = ballPossIndicator 
								* ( 1.0 + 0.85 * ( Math.random() - 0.5 ) );
			
			whoseBallIs = NEUTRAL_BALL;
	
			// make the decision about ball possession
			if( ballPossIndicator < -ballPossThreshold ) 
				whoseBallIs = OUR_BALL;		
			else if( ballPossIndicator > ballPossThreshold )
				whoseBallIs =  THEIR_BALL;		
			else 
				whoseBallIs = NEUTRAL_BALL; 	
	
			// limit the indicator growth 
			if( ballPossIndicator < -2.0 ) 
				ballPossIndicator = -2.0;
				
			if( ballPossIndicator > 2.0 ) 
				ballPossIndicator = 2.0;
				
			// if ball is grabbed, override all above
			if ( aWorldData.getBall().isGrabbed ) {
				if ( aWorldData.getBall().controllerType == myside ) {
					whoseBallIs = OUR_BALL;		// grabbed by own goalie (including myself)
					ballPossIndicator = -2.0;
				} else {
					whoseBallIs = THEIR_BALL;		// grabbed by opponent goalie
					ballPossIndicator = 2.0;
				}
			}
		} else {
			// ball possession when game is interrupted by referee
			if ( sideToContinue == aWorldData.getMyself().side ) { 
				ballPossIndicator = -2.0;
				whoseBallIs = OUR_BALL;
			} else {
				ballPossIndicator = 2.0;
				whoseBallIs = THEIR_BALL;
			}
		}
		
	} // determineBallPossession
	

	/*******************************************
	 *
	 * miscellaneous computations 
	 *
	 *******************************************/	 

		
	// This method determines the player who is the nearest to the ball
	// class variables 'minPlayerDistance', 'minTmmDistance', and 
	// 'minOppDistance' could be used in other methods
	//
	private void determineNearestPlayerToBall()
	{
		nearestOppToBall = null;
		minOppDistance = 200.0;	
		
		for( int i = 0; i < aWorldData.getTheirTeam().size(); i++ ) {
			Player player = (Player) aWorldData.getTheirTeam().elementAt( i );
			double dis2ball = player.position.distance( aWorldData.getBall().position );
			if( dis2ball < minOppDistance ) {
				minOppDistance = dis2ball;
				nearestOppToBall = player;
			}
		}
		
		nearestTmmToBall = null;
		minTmmDistance = 200.0;	

		for( int i = 0; i < aWorldData.getMyTeam().size(); i++ ) {
			Player player = (Player) aWorldData.getMyTeam().elementAt( i );
			double dis2ball = player.position.distance( aWorldData.getBall().position );
			if( dis2ball < minTmmDistance ) {
				minTmmDistance = dis2ball;
				nearestTmmToBall = player;
			}
		}

		if ( minTmmDistance < minOppDistance ) {
			nearestPlayerToBall = nearestTmmToBall;
			minPlayerDistance = minTmmDistance; 	
		} else {
			nearestPlayerToBall = nearestOppToBall; 
			minPlayerDistance = minOppDistance; 	
		}	
				
	}


	// this method determines where I player am planning to move to 
	// if I have created the offside situation
	//
	public void determineWhereToMoveOffSide()
	{
		// I am getting away from the opponent side 
		destination.setXY( aWorldData.getMyself().position.getX() - 15, 
							aWorldData.getMyself().position.getY() );
	}


	// this method determines where I player am planning to move to 
	// without the ball; it updates the class variable 'destination'
	// this is a placeholder method implementing very primitive tactics
	//
	public void determineWhereToMove()
	{
		if ( gamePeriod != RefereeData.FIRST_HALF &&
			 gamePeriod != RefereeData.SECOND_HALF ) { 	// not actual game play
			// ** pre-game or between halves **
			// I am just going to my home position 
			
			setMoveToHomePosAction();
		
		} else {
			// ** first or second half **
			
			if ( gameMode == RefereeData.BEFORE_KICK_OFF ||	
			     gameMode == RefereeData.KICK_OFF_L ||
			     gameMode == RefereeData.KICK_OFF_R ) {
				
				// I am just going to my home position 
				setMoveToHomePosAction();
				
			} else if ( gameMode == RefereeData.THROW_IN_L ||	
			     gameMode == RefereeData.THROW_IN_R ||
			     gameMode == RefereeData.CORNER_KICK_L ||
			     gameMode == RefereeData.CORNER_KICK_R || 
			     gameMode == RefereeData.GOAL_KICK_L ||
			     gameMode == RefereeData.GOAL_KICK_R ||
			     gameMode == RefereeData.OFFSIDE_L ||
			     gameMode == RefereeData.OFFSIDE_R ) {
		
				// ** game was interrupted by the referee **
				
				if ( iAmNearestTmmToBall && 
							aWorldData.getMyself().side == sideToContinue ) {
					actionType = CHASE;		// trying to repossess the ball
				} else {
					// I decide to move somewhere without the ball.
					determinePlayerPosSpecial(); 
				}
			
			} else {
				// ** regular game play; I am not possessing or chasing the ball **
				// this method is of critical importance, as 90 per cent
				// of the time it determines my actions
				// figure it out where to move to.
				// possible improvements:
				// (1) if the ball is controlled by us, I should open myself for receiving pass
				// (2) if the ball is controlled by them, I should be blocking the closest 
				//     opponent from receiving the pass by him
				// (3) attackers and defenders should be using different tactics
				
				determinePlayerPos(); 
			}
		}
	}
	

	// this method sets class variables to ensure that I go to home position
	private void setMoveToHomePosAction()
	{
		double distance2Home 	= aWorldData.getMyself().position
													.distance( homePos );
		
		if ( distance2Home > 1.5 ) {
			// keep moving to home position
			destination.setXY( homePos );	
		} else {
			// turn to ball
			direction2Ball 	= aWorldData.getMyself().position
								.direction( aWorldData.getBall().position );
			if ( Math.abs( aWorldData.getMyself()
									.direction - direction2Ball ) > 5.0 ) {
				actionType = TURN;
				facingPos.setXY( aWorldData.getBall().position );	
			} else 
				actionType = NOACTION;	
		}
	}
	
	
	// this is a placeholder method.
	// it determines the position where I player should move and updates my 
	// 'destination' which is a weighed sum of my 'reference' 
	// position and the ball position
	//
	private void determinePlayerPos()
	{
		double weight; 	
		double xball = aWorldData.getBall().position.getX();
		double yball = aWorldData.getBall().position.getY();
		double xavg;
		double yavg;		
		
		weight = 0.30; 			// magic number
		Vector2d refPsn;		// my 'reference' position
		
		if( whoseBallIs == OUR_BALL ) 	
			refPsn = offensePos;	
		else if( whoseBallIs == THEIR_BALL ) 
			refPsn = defensePos;	
		else 
			refPsn = homePos;
		
		double xref = refPsn.getX(); 
		double yref = refPsn.getY();
		
		double weight2 = 1.0;
		if ( amIGoalie() )
			weight2 = 0.33; 

		
		// weighted average coordinates
		xavg = xball*weight*weight2 + xref*(1-weight*weight2);
		yavg = yball*weight + yref*(1-weight);	
		
		// prevent the goalie from leaving the goal area
		if ( amIGoalie() ) {
			if ( Math.abs( yavg ) > PENALTY_CENTER )
				yavg = Util.sign( yavg ) * PENALTY_CENTER;
		}
			
		destination.setXY( new Vector2d( xavg, yavg ) ); 
		//System.out.println( "side=" + side + " destination = " + destination );
	}
		

	// this is a stub.
	// this method determines the position where I player should move in 
	// the special situations other than regular game play
	//
	private void determinePlayerPosSpecial()
	{
		determinePlayerPos();
	}
	
	
	public int countOpponentsInCircle( Vector2d center, double radius ) 
	{
		// get opponents
		int count = 0;
		
		for( int i = 0; i < aWorldData.getTheirTeam().size(); i++ ) {
			Player player = (Player) aWorldData.getTheirTeam().elementAt( i );
			double dis2opponent = player.position.distance( center );
			if( dis2opponent < radius ) 
				count++;
		}
		return count;
	}
		
			
	public boolean isInOffSide( Vector2d pos ) 
	{
		return false; 
	}

	// returns true is pos is inside own penalty area
	public boolean isInOwnPenaltyArea( Vector2d pos ) 
	{
		boolean onTheOwnSide =	( pos.getX() < 0 );
		
		if ( onTheOwnSide ) {
		
			//System.out.println("pos is on own side");
			
			boolean horizontalOK = LENGTH/2 - Math.abs( pos.getX() ) < PENALTY_DEPTH; 
			boolean verticalOK = Math.abs( pos.getY() ) < PENALTY_WIDTH/2.0; 
			
			//System.out.println("horizontalOK = " + horizontalOK );
			//System.out.println("verticalOK = "   + verticalOK );
			
			return 
				( horizontalOK && verticalOK && Math.abs( pos.getX() ) <= LENGTH/2 );
		} else 
			return false; 
	}
	
	// this method returns true if the position is inside own penalty area
	// within some margin (negative inside, positive outside)
	public boolean inPenaltyArea( Vector2d pos, double margin )
	{
		boolean horizontalOK = false;
		boolean verticalOK = Math.abs( pos.getY() ) 
								< PENALTY_WIDTH/2.0 + margin; 
		
		if ( pos.getX() < 0 ) {
			horizontalOK =  ( LENGTH/2 + pos.getX() ) 
								< ( PENALTY_DEPTH + margin );
		} else
			return false; 

		return 
			( horizontalOK && verticalOK && Math.abs( pos.getX() ) <= LENGTH/2 );
	}
	
	

	/*******************************************
	 *
	 * public get/set access methods for class variables
	 *
	 *******************************************/
	 
	public WorldData getWorldData()
	{
		return aWorldData;
	}

	public boolean amIGoalie()
	{
		return ( role == 0 );
	}		

	public boolean amInearestToBall()
	{
		return iAmNearestToBall;
	}		


	public boolean amInearestTmmToBall()
	{
		return iAmNearestTmmToBall;
	}		

	public boolean amIOffside()
	{
		return iAmOffside;
	}		

	public boolean isBallKickable()
	{
		return isBallKickable;
	}		

	public boolean isMyTeamOffside()
	{
		return myTeamIsOffside;
	}		


	public int getActionType()
	{	
		return actionType;
	}
	
	public void setActionType( int at )
	{
		actionType = at;
	}
		
	public Vector2d getHomePos()
	{
		return homePos;
	}

 	public Vector2d getDestination()
	{
		return destination; 
	}

	public double getDistance2Ball()
	{
		return distance2Ball;	
	}

 	public Vector2d getBallVelocity()
	{
		return ballVelocity;
	}

 	public Vector2d getMyVelocity()
	{
		return myVelocity;
	}
	
	public void setDashForce( double df )
	{
		dashForce = df;
	}

	public double getDashForce()
	{
		return dashForce;	
	}
	
	public void setDashDirection( double dd )
	{
		dashDirection = dd;
	}

	public double getDashDirection()
	{
		return dashDirection;	
	}

	public void setKickForce( double kf )
	{
		kickForce = kf;
	}

	public double getKickForce()
	{
		return kickForce;	
	}
	
	public void setKickDirection( double kd )
	{
		kickDirection = kd;
	}

	public double getKickDirection()
	{
		return kickDirection;	
	}

	public Vector2d getOppGoal()
	{
		return oppGoal;
	}

	public void setDestination( Vector2d pos )
	{
		destination = pos; 
	}

	public void setActionTime( int t )
	{
		actionTime = t; 
	}

	public int getBallPossession()
	{
		return whoseBallIs; 
	}
	
	public void setBallPossession( int possession )
	{
		whoseBallIs = possession; 
	}
	
	public int getGameMode()
	{
		return gameMode; 
	}	

	public int getGamePeriod()
	{
		return gamePeriod; 
	}	

	public char getMySide()
	{
		return myside; 
	}	

	public Vector2d getFacingPos()
	{
		return facingPos;
	}

	public void setFacingPos( Vector2d pos )
	{
		facingPos = pos;
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

 	/*******************************************
	 *
	 *  Formation methods
	 *
	 *******************************************/
    
    // this method sets my position in my team's formation
	public void setFormation( Formation formation )
	{
		this.aFormation = formation;
		// figure out where my place in the team formation is
		setDefaultFormationParams(); 
	}

	
    // This method sets my default home, defensive, and offensive positions
    // with respect to my role. All the magic numbers are 
    // worthy to experiment with.  
	private void setDefaultFormationParams()
	{
		double deltaXdef = 0;
		double deltaXoff = 0;
		
		homePos = new Vector2d( aFormation.getHome( role ) );
		offensePos = new Vector2d( aFormation.getHome( role ) );
		defensePos = new Vector2d( aFormation.getHome( role ) );
		
		if ( aFormation.isDefender( role ) ) {
			deltaXdef = 20;	
			deltaXoff = 20;	
		} else if ( aFormation.isMidfielder( role ) ) {
			deltaXdef = 15;	
			deltaXoff = 30;	
		} else if ( aFormation.isAttacker( role ) ) {
			deltaXdef = 15;	
			deltaXoff = 40;	
		} 
		
		if ( amIGoalie() ) {
			deltaXdef = 0;	
			deltaXoff = 0;	
		}
		
		offensePos.setX( offensePos.getX() + deltaXoff );
		defensePos.setX( defensePos.getX() - deltaXdef );
				
		//if ( role == 10 )
			//System.out.println( "side = " + side + " homePos = " + homePos 
				 //+ "\n defensePos = " + defensePos + " offensePos = " + offensePos); 
	}

}
