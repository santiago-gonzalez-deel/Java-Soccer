/*
 * SFU_Basic_Team.java The AI players start program
 * 
 * Copyright (C) 2001 Yu Zhang 
 * Modified by Vadim Kyrylov (October 2004; January 2006)
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 *
 *
 * by Vadim Kyrylov
 * January 2006
 *
 */
// NOTE: all this application is better to view with the soccer agent' standapoint.
//       So you will find comments like "I player", "Should I do something", etc. 

package tos_teams.africa;

import java.io.*;
import java.util.*;
import java.net.*;
import soccer.common.*;

// this class reperesents the basic soccer team that could be used
// for the experimentation

public class Africa_Team 
{	
	// This application implements soccer players as a set of threads 
	// communicating with the soccer server. Therefore, this application 
	// can contain from 1 to 22 player threads belonging to one or both teams 
	// playing the game. A team may be split between several replicas of 
	// this application; by default, both teams are implemented in a single 
	// application.
	
	// 'InetAddress' and 'port' are used for connecting the client threads 
	// to the server.
	// The default settings allow running the server and the players on 
	// the same computer. In a distributed settings, or with just one team
	// running, these values must be initialized accordaingly, 
	// e.g. by using the initialization file.
	
	public static InetAddress address;		
	public static int port = 7777;	
	private static String host = "localhost";
	
	// the team sizes are being set in setProperties() below
	public static int leftSize;	
	public static int rightSize;	
	
	private Vector<AIPlayer> robots = new Vector<AIPlayer>();


	public Africa_Team() 
	{
		try {
			address = InetAddress.getByName(host);
		} catch (Exception e) {
			System.out.println("Network error:" + e);
			System.exit(1);
		}

		// create team formations, as necessary
		// leftSize, rightSize could be read from the initialization file 
		Formation frmR = null, frmL = null;
		System.out.println();
		if ( leftSize > 0 ) {
			String formationL = "343";
			System.out.println( "Set formation for the LEFT team: " + formationL );
			frmL = new Formation(formationL);
		}
		if ( rightSize > 0 ) {
			String formationR = "343";
			System.out.println( "Set formation for the RIGHT team: " + formationR );
			frmR = new Formation(formationR);
		}
		System.out.println();
		
		/*
		// initialize left-hand team with a hard-coded formation
		
		for (int i = 0; i < leftSize; i++) {
			initAIPlayer('l', i, frmL );
		}

		// initialize right-hand team  with a hard-coded formation
		Formation frm2 = new Formation("523");
		for (int i = 0; i < rightSize; i++) {
			initAIPlayer('r', i, frmR );
		}
		
		*/
		
		// ** this is a more symmetrical way to initialize teams **
		// (to make the player performance same on the left and right side)
		
		System.out.println(" ---  players  ---\n");
		int maxSize = Math.max( leftSize, rightSize );
		for (int i=0; i < maxSize; i++ ) {
			for ( int k=0; k < 2; k++ ) {
				if ( k == 1 ) {
					if ( i < leftSize ) 
						initAIPlayer('l', i, frmL );
				} else {	
					if ( i < rightSize ) 
						initAIPlayer('r', i, frmR );
				}
			}
		}
		System.out.println();		
		
	}

	
	// initialize the soccer player (i.e. "robot").
	// this client first sends CONNECT packet to the server.
	// the connection is established once the acknowledging 
	// INIT packet is received from the server. 
	// 
	private void initAIPlayer(	char side,		// determines the team 
							int role,			// determines the role (0 is the goalie) 
							Formation formation) 
	{
		try {
			Transceiver transceiver = new Transceiver(false);

			// Send the connect packet to server
			// In this implementaion, the first player is registered 
			// as the goalie; the communication protocl allows 
			// making goalie registration even from different application
			
			char agentRole;
			if ( role == 0 )
				 agentRole = ConnectData.GOALIE;
			else {
				if ( formation.isKicker(role) )
					agentRole = ConnectData.FIELD_PLAYER_KICKER;
				else
					agentRole = ConnectData.FIELD_PLAYER;
			}
			 
			ConnectData connect;
			char teamside;
			
			// tell the server on what side this player is, his role, and 
			// what his real home position is
			if (side == 'l')
				teamside = ConnectData.LEFT;
			else
				teamside = ConnectData.RIGHT;
			
			connect = new ConnectData(ConnectData.PLAYER, 
									  teamside,
									  agentRole, 
									  WorldData.getRealPos(teamside, 
									  			formation.getHome(role)) );
				
			
			Packet connectPacket = new Packet(	Packet.CONNECT, 
												connect, 
												address,
												port);
			transceiver.send(connectPacket);
			//System.out.println("sent connectPacket: " + connectPacket.writePacket() );

			// wait for the acknowledging message from the server
			transceiver.setTimeout(1000);
			int limit = 0;
			Packet packet = null;

			while (limit < 60)
				try {
					packet = transceiver.receive();
					if (packet.packetType == Packet.INIT) {
						//System.out.println("received packet: " + packet.writePacket() );
						InitData initData = (InitData) packet.data;
						// create a thread for player
						AIPlayer robot = new AIPlayer(transceiver, 
												initData, 
												side, 
												role, 
												formation );  
						
						if (side == 'l')
							robot.setPlayerTeamID(1);
						else
							robot.setPlayerTeamID(-1);
							
						robot.setPlayerNumber(role + 1);
						
						// override the default value using this message from server
						WorldModel.MAX_GRABBED_STEPS = initData.maxGrabSteps; 
						//System.out.println("received: initData.maxGrabSteps = " + initData.maxGrabSteps);
						robot.getWorldModel().setFormation( formation ); 
						robot.getWorldModel().setPlayerTeamID( robot.getPlayerTeamID() );
						robot.getWorldModel().setPlayerNumber( robot.getPlayerNumber() );
						
						robots.addElement(robot);
						robot.start(); // start the player thread
						break;
					}
					transceiver.send(connectPacket);
					limit++;					
					
				} catch (Exception e) {
					System.out.println("player " + role+1 + ", side=" + side 
									+ " fails to communicate with server.");
				}

			transceiver.setTimeout(0);
			if (packet == null) {
				System.out.println("waiting for server: Timeout.");
				return;
			}

		} catch (Exception e) {
			System.out.println("Error during start up: " + e);
			return;
		}
	}


	public static void main(String argv[]) throws IOException 
	{
		System.out.println("\n ***  Starting SFU educational team. version 1.5.1  *** \n");
		
		Properties properties = new Properties();
		String configFileName = null;

		try {
			// First parse the parameters, if any
			for (int c = 0; c < argv.length; c += 2) {
				if (argv[c].compareTo("-pf") == 0) {
					configFileName = argv[c + 1];
					File file = new File(configFileName);
					if (file.exists()) {
						System.out.println("Load properties from file: "
								+ configFileName);
						properties = new Properties();
						properties.load(new FileInputStream(configFileName));
					} else {
						System.out.println("Properties file <" + configFileName
								+ "> does not exist. Using defaults.");
					}
				} else {
					System.out.println("Wrong arguments for the Properties file. Using defaults.");
					throw new Exception();
				}
			}
		} catch (Exception e) {
			System.err.println("");
			System.err.println("USAGE: SFU_Basic_Team -pf property_file_name]");
			return;
		}

		setProperties(properties);

		Africa_Team players = new Africa_Team();

	}
	//---------------------------------------------------------------------------
	/**
	 * set properties
	 *
	 * this method could be enhanced by setting whatever properties you want
	 * using the parameter initialization file
	 */
	public static void setProperties(Properties properties) 
	{
		leftSize = Integer.parseInt(properties.getProperty("left_ream_size", "11"));

		rightSize = Integer.parseInt(properties
				.getProperty("right_ream_size", "11"));

		host = properties.getProperty("host_address", "localhost");

		port = Integer.parseInt(properties.getProperty("port_number", "7777"));

	}
}