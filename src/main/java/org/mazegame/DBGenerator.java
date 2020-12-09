package org.mazegame;

import java.io.*;
import java.sql.*;
import java.util.*;
import org.dom4j.*;
import org.dom4j.io.SAXReader;
import org.apache.commons.lang3.StringUtils;

public class DBGenerator {
	public enum Direction {
		TOP, BOTTOM, LEFT, RIGHT;

		String toLowerCase() {
			return name().toLowerCase();
		}
	}

	enum doorStatus {
		OPEN, CLOSED, FAILED
	}

	// build the whole data structure
	public static void init() {
		// SQLite connection string
		String url = "jdbc:sqlite:./java-sqlite.db";

		// SQL statement for creating a new table
		String createRoomsSql = "CREATE TABLE IF NOT EXISTS rooms (" + " id integer PRIMARY KEY,"
				+ " position text NOT NULL," + " top_door_id integer," + " right_door_id integer,"
				+ " bottom_door_id integer," + " left_door_id integer," + " name text NOT NULL," + " capacity real)";

		String createDoorsSql = "CREATE TABLE IF NOT EXISTS doors (" + " id integer PRIMARY KEY," + " top_room_id,"
				+ " bottom_room_id," + " left_room_id," + " right_room_id," + " status text NOT NULL,"
				+ " name text NOT NULL)";

		String createQuestionsSql = "CREATE TABLE IF NOT EXISTS questions (" + " id integer PRIMARY KEY,"
				+ " question text NOT NULL," + " answers text NOT NULL," + " right_answer text NOT NULL,"
				+ " door_id integer," + " foreign key(door_id) references doors(id)" + ")";

		try {
			Connection conn = DriverManager.getConnection(url);
			Statement stmt = conn.createStatement();
			stmt.execute(createRoomsSql);
			stmt.execute(createDoorsSql);
			stmt.execute(createQuestionsSql);

			// load all questions to the database
			// 5*6 => 4*6+5*5 = 49 doors we need at least 49 questions
			try {
				SAXReader reader = new SAXReader();
				Document read = reader.read("./questions.xml");
				Element root = read.getRootElement();
				Iterator dataraw = root.elementIterator();

				while (dataraw.hasNext()) {
					Element questions = (Element) dataraw.next();

					String questionText = "";
					String answersText = "";
					String rightAnswerText = "";

					// get answers of question
					Iterator<?> answers = questions.elementIterator();
					while (answers.hasNext()) {
						Element answer = (Element) answers.next();

						if (answer.getName() == "question") {
							questionText = answer.getStringValue();
						}

						if (answer.getName() == "right_answer") {
							rightAnswerText = answer.getStringValue();
						}

						if (answer.getName() == "answer") {
							if (StringUtils.isBlank(answersText)) {
								answersText += answer.getStringValue();
							} else {
								answersText += "," + answer.getStringValue();
							}
						}
					}

					stmt.execute("INSERT INTO questions(question, answers, right_answer) VALUES('" + questionText
							+ "', '" + answersText + "', '" + rightAnswerText + "');");
				}
			} catch (DocumentException e) {
				e.printStackTrace();
			}

			// generate doors and assign question to door, in 5*6 room placement we need
			// 5*6 => 4*6+5*5 = 49 doors
			int doorSize = 49;
			ResultSet rs = stmt.executeQuery("SELECT * FROM questions ORDER BY RANDOM() limit " + doorSize + "");
			List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
			ResultSetMetaData md = rs.getMetaData();
			int columnCount = md.getColumnCount();

			// loop through the result set
			while (rs.next()) {
				Map<String, Object> rowData = new HashMap<String, Object>();
				for (int i = 1; i <= columnCount; i++) {
					rowData.put(md.getColumnName(i), rs.getObject(i));
				}
				list.add(rowData);
			}

			for (int i = 0; i < list.size(); i++) {
				stmt.execute("INSERT INTO doors(name, status) VALUES('test door','" + doorStatus.CLOSED + "');");
				ResultSet resultSet = stmt.getGeneratedKeys();
				if (resultSet.next()) {
					stmt.executeUpdate("UPDATE questions SET door_id = " + resultSet.getInt(1) + " where id = "
							+ ((Map<String, Object>) list.get(i)).get("id") + "");
				}
			}

			// generate rooms and assign doors to room, in a 5*6 room placement we need 30
			// rooms
			int[][] roomIndex = new int[5][6];
			int room_id_increased = 1;
			int offset = 0;

			for (int i = 0; i < roomIndex.length; i++) {
				for (int j = 0; j < roomIndex[0].length; j++) {
					roomIndex[i][j] = room_id_increased;
					System.out.println("room id:" + roomIndex[i][j]);
					System.out.println("position" + i + "， " + j);

					room_id_increased++;
					Map<Integer, Integer> id_records = new HashMap<Integer, Integer>();
					Map<String, String> room = new HashMap<String, String>();

					// calculate boundary
					if (i == 0) {
						room.put("top", "none");
					}
					if (i == 4) {
						room.put("bottom", "none");
					}
					if (j == 0) {
						room.put("left", "none");
					}
					if (j == 5) {
						room.put("right", "none");
					}

					// check if left or top door exist and reuse
					if (i != 0) {
						// get top_door_id
						int index_x = i - 1;
						int index_y = j;
						if (index_x >= 0 && index_y >= 0) {
							int top_room_id = roomIndex[index_x][index_y];
							ResultSet top_room = stmt
									.executeQuery("SELECT * FROM rooms WHERE id = " + top_room_id + "");
							while (top_room.next()) {
								room.put("top", top_room.getString("bottom_door_id"));
								stmt.executeUpdate("UPDATE doors SET bottom_room_id = " + roomIndex[i][j]
										+ " where id = " + top_room.getString("bottom_door_id") + "");
							}
						}
					}

					if (j != 0) {
						// get left_door_id
						int index_x = i;
						int index_y = j - 1;
						if (index_x >= 0 && index_y >= 0) {
							int left_room_id = roomIndex[index_x][index_y];
							ResultSet left_room = stmt
									.executeQuery("SELECT * FROM rooms WHERE id = " + left_room_id + "");
							while (left_room.next()) {
								room.put("left", left_room.getString("right_door_id"));
								stmt.executeUpdate("UPDATE doors SET right_room_id = " + roomIndex[i][j]
										+ " where id = " + left_room.getString("right_door_id") + "");
							}
						}
					}

					// fill in doors
					if (!room.containsKey("top")) {
						ResultSet door = stmt
								.executeQuery("SELECT * FROM doors ORDER BY id limit 1 offset " + offset + "");
						offset++;
						while (door.next()) {
							room.put("top", door.getString("id"));
							stmt.executeUpdate("UPDATE doors SET bottom_room_id = " + roomIndex[i][j] + " where id = "
									+ door.getString("id") + "");
						}
					}

					if (!room.containsKey("bottom")) {
						ResultSet door = stmt
								.executeQuery("SELECT * FROM doors ORDER BY id limit 1 offset " + offset + "");
						offset++;
						while (door.next()) {
							room.put("bottom", door.getString("id"));
							stmt.executeUpdate("UPDATE doors SET top_room_id = " + roomIndex[i][j] + " where id = "
									+ door.getString("id") + "");
						}
					}

					if (!room.containsKey("left")) {
						ResultSet door = stmt
								.executeQuery("SELECT * FROM doors ORDER BY id limit 1 offset " + offset + "");
						offset++;
						while (door.next()) {
							room.put("left", door.getString("id"));
							stmt.executeUpdate("UPDATE doors SET right_room_id = " + roomIndex[i][j] + " where id = "
									+ door.getString("id") + "");
						}
					}

					if (!room.containsKey("right")) {
						ResultSet door = stmt
								.executeQuery("SELECT * FROM doors ORDER BY id limit 1 offset " + offset + "");
						offset++;
						while (door.next()) {
							room.put("right", door.getString("id"));
							stmt.executeUpdate("UPDATE doors SET left_room_id = " + roomIndex[i][j] + " where id = "
									+ door.getString("id") + "");
						}
					}
					System.out.println("fill in times" + offset);
					// record position and data
					stmt.executeUpdate(
							"INSERT INTO rooms(id, name, capacity, position, top_door_id, bottom_door_id, left_door_id, right_door_id) VALUES('"
									+ roomIndex[i][j] + "', 'test room', '4', '" + i + ", " + j + "', '"
									+ room.get("top") + "', '" + room.get("bottom") + "', '" + room.get("left") + "', '"
									+ room.get("right") + "');");
				}
			}

			System.out.println("Data initialization completed!");
			stmt.close();
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}
	}

	// to determine the game is able to continue by the data
	private static Boolean continuePlay() {
		return false;
	}

	// get the door placement info and room position by room_id
	public static Map<String, String> getRoomDetail(Integer room_id) {
		String url = "jdbc:sqlite:./java-sqlite.db";

		Map<String, String> room = new HashMap<String, String>();

		try {
			Connection conn = DriverManager.getConnection(url);
			Statement stmt = conn.createStatement();

			ResultSet data = stmt.executeQuery("SELECT * FROM rooms WHERE id = " + room_id + "");
			if (data.next()) {
				room.put("id", data.getString("id"));
				room.put("position", data.getString("position"));
				room.put("top_door_id", data.getString("top_door_id"));
				room.put("bottom_door_id", data.getString("bottom_door_id"));
				room.put("left_door_id", data.getString("left_door_id"));
				room.put("right_door_id", data.getString("right_door_id"));
			}
			stmt.close();
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}

		System.out.println(room);

		return room;
	}

	// get the question and door info and door_id and direction
	public static Map<String, String> getDoorDetail(Integer door_id, Direction direction) {
		String url = "jdbc:sqlite:./java-sqlite.db";

		Map<String, String> door = new HashMap<String, String>();

		try {
			Connection conn = DriverManager.getConnection(url);
			Statement stmt = conn.createStatement();

			ResultSet doorData = stmt.executeQuery("SELECT * FROM doors WHERE id =  " + door_id + "");

			if (doorData.next()) {
				door.put("id", doorData.getString("id"));
				door.put("status", doorData.getString("status"));
				door.put("next_room_id", doorData.getString(direction.toLowerCase() + "_room_id"));
			}

			ResultSet questionData = stmt.executeQuery("SELECT * FROM questions WHERE door_id =  " + door_id + "");
			if (questionData.next()) {
				door.put("question", questionData.getString("question"));
				door.put("answers", questionData.getString("answers"));
			}

			stmt.close();
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}

		System.out.println(door);

		return door;
	}

	// get the door status by answer the question
	public static doorStatus answerQuestion(Integer question_id, String answer) {
		String url = "jdbc:sqlite:./java-sqlite.db";

		Map<String, String> result = new HashMap<String, String>();

		try {
			Connection conn = DriverManager.getConnection(url);
			Statement stmt = conn.createStatement();

			Boolean answer_right = false;
			Integer door_id = 0;

			ResultSet questionData = stmt.executeQuery("SELECT * FROM questions WHERE id =  " + question_id + "");
			if (questionData.next()) {
				answer_right = answer.equals(questionData.getString("right_answer"));

				door_id = questionData.getInt("door_id");
			}

			if (door_id <= 0) {
				return doorStatus.CLOSED;
			}

			ResultSet doorData = stmt.executeQuery("SELECT * FROM doors WHERE id =  " + door_id + "");

			if (doorData.next()) {
				if (doorData.getString("status") == doorStatus.OPEN.toString()) {
					return doorStatus.OPEN;
				}

				if (doorData.getString("status") == doorStatus.FAILED.toString()) {
					return doorStatus.FAILED;
				}
				
				if (answer_right) {
					stmt.executeUpdate(
							"UPDATE doors SET status = '" + doorStatus.OPEN + "' where id = " + door_id + "");
					return doorStatus.OPEN;
				} else {
					return doorStatus.FAILED;
				}
			}

			stmt.close();
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}

		return doorStatus.CLOSED;
	}

	public static void deleteDB() {
		try {
			File file = new File("./java-sqlite.db");
			if (file.delete()) {
				System.out.println("DB clean");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		init();
	}
}
