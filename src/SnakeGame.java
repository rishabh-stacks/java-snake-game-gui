
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

// A self-contained Snake game built with Java and Swing only.
public final class SnakeGame {

    // Utility-style entry class: prevents instantiation.
    private SnakeGame() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { }
            JFrame frame = new JFrame("SNAKE / CLASSIC ARCADE");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.setContentPane(new GamePanel());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }


    //  GamePanel – Game Logic + Rendering
    private static final class GamePanel extends JPanel {
        private static final int CELL = 24, COLS = 25, ROWS = 21;
        private static final int BOARD_W = CELL * COLS, BOARD_H = CELL * ROWS;
        private static final Color BG = new Color(12, 18, 28);
        private static final Color PANEL = new Color(20, 29, 43);
        private static final Color GREEN = new Color(91, 230, 142);
        private static final Color MUTED = new Color(145, 163, 181);
        private final java.util.List<Point> snake = new ArrayList<>();
        private final Random random = new Random();
        private final javax.swing.Timer timer;
        private Point food;
        private Direction direction = Direction.RIGHT, queuedDirection = Direction.RIGHT;
        private GameState state = GameState.READY;
        private int score, highScore, ticks;
        private boolean movedThisTick;

        // Builds the panel, loads the saved high score and starts the game loop.
        GamePanel() {
            setPreferredSize(new Dimension(BOARD_W + 48, BOARD_H + 132));
            setBackground(BG);
            setFocusable(true);
            highScore = loadHighScore();
            resetGame();
            timer = new javax.swing.Timer(115, e -> tick());
            timer.start();
            installKeys();
        }

        // Input Handling
        private void installKeys() {
            bind("UP", "up", () -> changeDirection(Direction.UP));
            bind("W", "w", () -> changeDirection(Direction.UP));
            bind("DOWN", "down", () -> changeDirection(Direction.DOWN));
            bind("S", "s", () -> changeDirection(Direction.DOWN));
            bind("LEFT", "left", () -> changeDirection(Direction.LEFT));
            bind("A", "a", () -> changeDirection(Direction.LEFT));
            bind("RIGHT", "right", () -> changeDirection(Direction.RIGHT));
            bind("D", "d", () -> changeDirection(Direction.RIGHT));
            bind("SPACE", "pause", () -> {
                if (state == GameState.PLAYING) state = GameState.PAUSED;
                else if (state == GameState.PAUSED) state = GameState.PLAYING;
                else if (state == GameState.READY || state == GameState.GAME_OVER) startNewGame();
                repaint();
            });
            bind("ENTER", "enter", () -> {
                if (state == GameState.READY || state == GameState.GAME_OVER) startNewGame();
                else if (state == GameState.PAUSED) state = GameState.PLAYING;
                repaint();
            });
            bind("R", "restart", () -> startNewGame());
        }

        // Connects a Key to an Action.
        private void bind(String key, String name, Runnable action) {
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
            getActionMap().put(name, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { action.run(); }
            });
        }

        // Handles a Direction Key Press.
        private void changeDirection(Direction next) {
            if (state == GameState.READY) state = GameState.PLAYING;
            if (state != GameState.PLAYING || movedThisTick) return;
            if (next != direction.opposite()) { queuedDirection = next; movedThisTick = true; }
        }


        //  Game lifecycle

        /* Resets all game variables to their starting values:
        a 3-segment snake in the middle of the board heading right, score 0, and a freshly placed food item. */
        private void resetGame() {
            snake.clear();
            int x = COLS / 2, y = ROWS / 2;
            snake.add(new Point(x, y)); snake.add(new Point(x - 1, y)); snake.add(new Point(x - 2, y));
            direction = queuedDirection = Direction.RIGHT;
            score = 0; ticks = 0; movedThisTick = false;
            spawnFood();
        }

        // Resets the game and immediately starts playing.
        private void startNewGame() { resetGame(); state = GameState.PLAYING; repaint(); }

        // One step of the game loop, called by the timer.
        private void tick() {
            if (state != GameState.PLAYING) { repaint(); return; }
            ticks++;
            direction = queuedDirection;
            Point head = snake.get(0);
            Point next = new Point(head.x + direction.dx, head.y + direction.dy);
            boolean eating = next.equals(food);

            // Self-collision check
            int collisionLimit = eating ? snake.size() : snake.size() - 1;
            if (next.x < 0 || next.x >= COLS || next.y < 0 || next.y >= ROWS || snake.subList(0, collisionLimit).contains(next)) {
                state = GameState.GAME_OVER;
                if (score > highScore) { highScore = score; saveHighScore(highScore); }
                repaint(); return;
            }
            snake.add(0, next);
            if (eating) { score += 10; spawnFood(); }
            else snake.remove(snake.size() - 1);
            movedThisTick = false;
            timer.setDelay(Math.max(58, 115 - score / 10 * 2));
            repaint();
        }

        // Places the food on a random cell that is not occupied by the snake. (Loops until a free cell is found)
        private void spawnFood() {
            do { food = new Point(random.nextInt(COLS), random.nextInt(ROWS)); }
            while (snake.contains(food));
        }

        //  High-score persistence (Reads the high score from disk)
        private int loadHighScore() {
            try { return Integer.parseInt(Files.readString(scorePath()).trim()); }
            catch (Exception ignored) { return 0; }
        }

        // Writes the high score to disk
        private void saveHighScore(int value) {
            try { Files.writeString(scorePath(), Integer.toString(value)); }
            catch (IOException ignored) { }
        }


        private Path scorePath() { return Paths.get(System.getProperty("user.home"), ".snake-game-highscore"); }

        //  Rendering
        //  Draws the complete frame: score boxes, board grid, food, snake,overlay (ready / paused / game over).
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int pad = 24, top = 94, left = pad;
            drawHeader(g, left, 22);
            drawStats(g, left, 58);
            int boardX = left, boardY = top;
            g.setColor(PANEL); g.fillRoundRect(boardX - 1, boardY - 1, BOARD_W + 2, BOARD_H + 2, 18, 18);
            g.setColor(new Color(34, 47, 63));
            for (int x = 0; x <= COLS; x++) g.drawLine(boardX + x * CELL, boardY, boardX + x * CELL, boardY + BOARD_H);
            for (int y = 0; y <= ROWS; y++) g.drawLine(boardX, boardY + y * CELL, boardX + BOARD_W, boardY + y * CELL);
            drawFood(g, boardX, boardY);
            drawSnake(g, boardX, boardY);
            if (state != GameState.PLAYING) drawOverlay(g, boardX, boardY);
            g.setColor(MUTED); g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.drawString("MOVE  ↑ ↓ ← →  /  W A S D", left, boardY + BOARD_H + 28);
            g.drawString("SPACE  PAUSE     R  RESTART", left + 250, boardY + BOARD_H + 28);
            g.dispose();
        }

        // Draws the title bar: accent stripe, "SNAKE" title, subtitle and the "Java Edition" badge
        private void drawHeader(Graphics2D g, int x, int y) {
            g.setColor(GREEN); g.fillRoundRect(x, y + 2, 7, 29, 4, 4);
            g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 23)); g.drawString("SNAKE", x + 18, y + 25);
            g.setColor(MUTED); g.setFont(new Font("SansSerif", Font.PLAIN, 11)); g.drawString("CLASSIC ARCADE", x + 100, y + 24);
            g.setColor(new Color(33, 53, 47)); g.fillRoundRect(x + BOARD_W - 115, y + 2, 115, 27, 13, 13);
            g.setColor(GREEN); g.setFont(new Font("SansSerif", Font.BOLD, 10)); g.drawString("●  JAVA EDITION", x + BOARD_W - 101, y + 19);
        }

        // Draws the two stat boxes: Current SCORE (left) and saved BEST Score (right).
        private void drawStats(Graphics2D g, int x, int y) {
            g.setColor(PANEL); g.fillRoundRect(x, y, 155, 27, 9, 9);
            g.setColor(MUTED); g.setFont(new Font("SansSerif", Font.BOLD, 10)); g.drawString("SCORE", x + 10, y + 17);
            g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 14)); g.drawString(String.format("%04d", score), x + 75, y + 18);
            g.setColor(PANEL); g.fillRoundRect(x + 165, y, 155, 27, 9, 9);
            g.setColor(MUTED); g.setFont(new Font("SansSerif", Font.BOLD, 10)); g.drawString("BEST", x + 175, y + 17);
            g.setColor(new Color(255, 207, 94)); g.setFont(new Font("SansSerif", Font.BOLD, 14)); g.drawString(String.format("%04d", highScore), x + 240, y + 18);
        }

        // Draws the food: a faint glow, a solid red dot and a small highlight.
        private void drawFood(Graphics2D g, int x, int y) {
            int fx = x + food.x * CELL, fy = y + food.y * CELL;
            g.setColor(new Color(255, 100, 115, 45)); g.fillOval(fx + 1, fy + 1, CELL - 2, CELL - 2);
            g.setColor(new Color(255, 100, 115)); g.fillOval(fx + 6, fy + 6, CELL - 12, CELL - 12);
            g.setColor(new Color(255, 190, 190)); g.fillOval(fx + 9, fy + 8, 4, 4);
        }

        // Draws every snake segment as a rounded square. Segments are drawn from tail to head
        // The body fades slightly darker towards the tail, and the head gets a lighter colour plus two eyes that face the current direction.
        private void drawSnake(Graphics2D g, int x, int y) {
            for (int i = snake.size() - 1; i >= 0; i--) {
                Point p = snake.get(i); int sx = x + p.x * CELL, sy = y + p.y * CELL;
                int inset = i == 0 ? 2 : 3;
                g.setColor(i == 0 ? new Color(132, 255, 177) : new Color(48, Math.max(125, 205 - i), 111));
                g.fillRoundRect(sx + inset, sy + inset, CELL - inset * 2, CELL - inset * 2, 8, 8);
                if (i == 0) {
                    g.setColor(new Color(13, 45, 31));
                    int ex1 = sx + 8, ey1 = sy + 7, ex2 = sx + 15, ey2 = sy + 7;
                    if (direction == Direction.LEFT || direction == Direction.RIGHT) { ex1 = sx + (direction == Direction.RIGHT ? 14 : 6); ex2 = ex1; ey1 = sy + 7; ey2 = sy + 15; }
                    else if (direction == Direction.UP) { ex1 = sx + 7; ex2 = sx + 15; ey1 = ey2 = sy + 6; }
                    else { ex1 = sx + 7; ex2 = sx + 15; ey1 = ey2 = sy + 15; }
                    g.fillOval(ex1, ey1, 3, 3); g.fillOval(ex2, ey2, 3, 3);
                }
            }
        }

        // Draws the semi-transparent message box shown in the READY, PAUSED and GAME_OVER states
        private void drawOverlay(Graphics2D g, int x, int y) {
            g.setColor(new Color(7, 12, 20, 190)); g.fillRoundRect(x, y, BOARD_W, BOARD_H, 16, 16);
            String title = state == GameState.READY ? "READY TO PLAY" : state == GameState.PAUSED ? "PAUSED" : "GAME OVER";
            String subtitle = state == GameState.READY ? "A little snack. A lot of points." : state == GameState.PAUSED ? "Take a breath. Your snake is waiting." : "Final score: " + score;
            g.setFont(new Font("SansSerif", Font.BOLD, 27)); FontMetrics fm = g.getFontMetrics();
            g.setColor(Color.WHITE); g.drawString(title, x + (BOARD_W - fm.stringWidth(title)) / 2, y + BOARD_H / 2 - 20);
            g.setFont(new Font("SansSerif", Font.PLAIN, 13)); fm = g.getFontMetrics(); g.setColor(MUTED);
            g.drawString(subtitle, x + (BOARD_W - fm.stringWidth(subtitle)) / 2, y + BOARD_H / 2 + 9);
            String action = state == GameState.PAUSED ? "PRESS SPACE OR ENTER TO RESUME" : "PRESS ENTER OR SPACE TO START";
            g.setFont(new Font("SansSerif", Font.BOLD, 11)); fm = g.getFontMetrics(); g.setColor(GREEN);
            g.drawString(action, x + (BOARD_W - fm.stringWidth(action)) / 2, y + BOARD_H / 2 + 42);
        }
    }


    //  Supporting types

    // The screens / phases of the game.
    private enum GameState { READY, PLAYING, PAUSED, GAME_OVER }

    // The four movement directions, each with its grid offset (dx = columns, dy = rows)
    private enum Direction {
        UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);
        final int dx, dy;
        Direction(int dx, int dy) { this.dx = dx; this.dy = dy; }
        Direction opposite() {
            return switch (this) { case UP -> DOWN; case DOWN -> UP; case LEFT -> RIGHT; case RIGHT -> LEFT; };
        }
    }
}
