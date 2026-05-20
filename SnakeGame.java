import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.LinkedList;
import java.util.Random;
import javax.swing.*;

/** Интерфейс **/
interface GameConstants {
    // Размеры окна и сетки
    int WINDOW_WIDTH = 800;
    int WINDOW_HEIGHT = 600;
    int CELL_SIZE = 20;
    int COLS = WINDOW_WIDTH / CELL_SIZE;
    int ROWS = WINDOW_HEIGHT / CELL_SIZE;

    // Начальные параметры змейки
    int INITIAL_BODY_LENGTH = 3;
    int INITIAL_DIRECTION = 0;

    // Задержка между кадрами (мс) и шаг ускорения
    int BASE_DELAY = 150;
    int SPEED_DECREMENT = 3;
    int MIN_DELAY = 80;

    // Цвета
    Color BG_COLOR = new Color(20, 20, 40);
    Color GRID_COLOR = new Color(45, 45, 65);
    Color SNAKE_HEAD_COLOR = new Color(160, 50, 240);
    Color SNAKE_BODY_COLOR = new Color(120, 30, 190);
    Color APPLE_COLOR = new Color(255, 80, 120);
    Color TEXT_COLOR = new Color(230, 230, 255);
    Color OVERLAY_COLOR = new Color(0, 0, 0, 160);

    // Шрифты
    Font FONT_SCORE = new Font("Arial", Font.BOLD, 24);
    Font FONT_BIG = new Font("Arial", Font.BOLD, 42);
    Font FONT_SMALL = new Font("Arial", Font.PLAIN, 18);

    // Клавиши команд
    int KEY_NEW_GAME = KeyEvent.VK_R;
    int KEY_PAUSE   = KeyEvent.VK_ESCAPE;
    int KEY_EXIT    = KeyEvent.VK_Q;

    // Клавиши направлений
    int KEY_UP    = KeyEvent.VK_W;
    int KEY_LEFT  = KeyEvent.VK_A;
    int KEY_DOWN  = KeyEvent.VK_S;
    int KEY_RIGHT = KeyEvent.VK_D;

    // Файл для хранения лучшего счёта
    String HIGH_SCORE_FILE = "snake_highscore.dat";
}

/** игровой движок **/
class GameEngine implements GameConstants {
    LinkedList<Point> snakeBody;
    LinkedList<Point> obstacles;
    int direction;
    int desiredDirection;
    Point apple;
    int score;
    int combo;
    int highScore;
    boolean running;
    boolean paused;
    boolean gameOver;
    int currentDelay;
    Thread gameThread;
    Random random;
    boolean goldenApple;

    public GameEngine() {
        snakeBody = new LinkedList<>();
        obstacles = new LinkedList<>();
        random = new Random();
        loadHighScore();
        resetGame();
        running = false;
        paused = false;
        gameOver = false;
        combo = 0;
    }

    /** Полный сброс игры к начальному состоянию **/
    private void resetGame() {
        snakeBody.clear();
        obstacles.clear();
        for (int i = 0; i < INITIAL_BODY_LENGTH; i++) {
            snakeBody.add(new Point(COLS / 2 - i, ROWS / 2));
        }
        direction = INITIAL_DIRECTION;
        desiredDirection = direction;
        score = 0;
        combo = 0;
        currentDelay = BASE_DELAY;
        goldenApple = false;
        spawnApple();
        generateObstacles();
        gameOver = false;
        paused = false;
    }

    /** Запустить новую игру **/
    public void newGame() {
        if (!gameOver) {
            checkHighScore();
        }
        resetGame();
        if (gameThread == null || !gameThread.isAlive()) {
            running = true;
            gameThread = new Thread(this::gameLoop);
            gameThread.start();
        } else {
            running = true;
        }
    }

    /** Загружает лучший счёт из файла **/
    private void loadHighScore() {
        try (BufferedReader reader = new BufferedReader(new FileReader(HIGH_SCORE_FILE))) {
            highScore = Integer.parseInt(reader.readLine());
        } catch (IOException | NumberFormatException e) {
            highScore = 0;
        }
    }

    /** Сохраняет рекорд в файл **/
    private synchronized void checkHighScore() {
        if (score > highScore) {
            highScore = score;
            try (PrintWriter writer = new PrintWriter(new FileWriter(HIGH_SCORE_FILE))) {
                writer.println(highScore);
            } catch (IOException ignored) {}
        }
    }

    /** Игровой цикл в отдельном потоке **/
    private void gameLoop() {
        while (running) {
            if (!paused && !gameOver) {
                update();
            }
            try {
                Thread.sleep(currentDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /** Генерация препятствий **/
    private void generateObstacles() {
        if (score >= 10 && obstacles.isEmpty()) {
            int obsCount = Math.min(5, score / 5);
            for (int i = 0; i < obsCount; i++) {
                Point obs;
                do {
                    obs = new Point(random.nextInt(COLS), random.nextInt(ROWS));
                } while (snakeBody.contains(obs) || (apple != null && apple.equals(obs)));
                obstacles.add(obs);
            }
        }
    }

    /** Основная логика одного шага **/
    private void update() {
        if ((desiredDirection + 2) % 4 != direction) {
            direction = desiredDirection;
        }

        Point head = snakeBody.getFirst();
        int newX = head.x;
        int newY = head.y;
        switch (direction) {
            case 0 -> newX++;
            case 1 -> newY++;
            case 2 -> newX--;
            case 3 -> newY--;
        }

        if (newX < 0 || newX >= COLS || newY < 0 || newY >= ROWS) {
            gameOver = true;
            running = false;
            checkHighScore();
            combo = 0;
            return;
        }

        Point newHead = new Point(newX, newY);
        
        if (obstacles.contains(newHead)) {
            gameOver = true;
            running = false;
            checkHighScore();
            combo = 0;
            return;
        }
        
        boolean eatApple = newHead.equals(apple);
        int checkLimit = eatApple ? snakeBody.size() : snakeBody.size() - 1;
        for (int i = 0; i < checkLimit; i++) {
            if (snakeBody.get(i).equals(newHead)) {
                gameOver = true;
                running = false;
                checkHighScore();
                combo = 0;
                return;
            }
        }

        snakeBody.addFirst(newHead);
        if (eatApple) {
            int points = goldenApple ? 5 : 2;
            score += points;
            combo++;
            
            if (combo > 3 && combo % 3 == 0) {
                currentDelay = Math.max(MIN_DELAY, currentDelay - SPEED_DECREMENT * 2);
            } else {
                if (currentDelay > MIN_DELAY) {
                    currentDelay = Math.max(MIN_DELAY, currentDelay - SPEED_DECREMENT);
                }
            }
            
            spawnApple();
            generateObstacles();
        } else {
            snakeBody.removeLast();
            combo = 0;
        }
    }

    /** Генерация яблока в случайной свободной клетке **/
    private void spawnApple() {
        int x, y;
        goldenApple = random.nextInt(100) < 20;
        do {
            x = random.nextInt(COLS);
            y = random.nextInt(ROWS);
        } while (snakeBody.contains(new Point(x, y)) || obstacles.contains(new Point(x, y)));
        apple = new Point(x, y);
    }

    public void setDesiredDirection(int dir) {
        desiredDirection = dir;
    }

    public void togglePause() {
        if (!gameOver) {
            paused = !paused;
        }
    }

    public void stopGame() {
        running = false;
        checkHighScore();
        if (gameThread != null) {
            gameThread.interrupt();
        }
    }
}

/** Панель отрисовки **/
class GamePanel extends JPanel implements GameConstants, Runnable, KeyListener {
    private GameEngine engine;
    private boolean repaintRunning;

    public GamePanel() {
        setPreferredSize(new Dimension(WINDOW_WIDTH, WINDOW_HEIGHT));
        setBackground(BG_COLOR);
        setFocusable(true);
        addKeyListener(this);
        engine = new GameEngine();
        engine.newGame();
        repaintRunning = true;
        new Thread(this).start();
    }

    @Override
    public void run() {
        while (repaintRunning) {
            repaint();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawGrid(g2d);
        drawObstacles(g2d);
        drawApple(g2d);
        drawSnake(g2d);
        drawScore(g2d);
        drawCombo(g2d);

        if (engine.gameOver) {
            drawOverlay(g2d, "ИГРА ОКОНЧЕНА", "R — заново | Q — выход");
        } else if (engine.paused) {
            drawOverlay(g2d, "ПАУЗА", "Esc — продолжить | R — заново | Q — выход");
        }
    }

    private void drawGrid(Graphics2D g) {
        g.setColor(GRID_COLOR);
        for (int i = 0; i <= COLS; i++) {
            g.drawLine(i * CELL_SIZE, 0, i * CELL_SIZE, WINDOW_HEIGHT);
        }
        for (int i = 0; i <= ROWS; i++) {
            g.drawLine(0, i * CELL_SIZE, WINDOW_WIDTH, i * CELL_SIZE);
        }
    }

    private void drawSnake(Graphics2D g) {
        int idx = 0;
        for (Point p : engine.snakeBody) {
            if (idx == 0) {
                GradientPaint grad = new GradientPaint(p.x * CELL_SIZE, p.y * CELL_SIZE, SNAKE_HEAD_COLOR,
                                         p.x * CELL_SIZE + CELL_SIZE, p.y * CELL_SIZE + CELL_SIZE, new Color(255, 150, 255));
                ((Graphics2D) g).setPaint(grad);
            } else {
                g.setColor(SNAKE_BODY_COLOR);
            }
            g.fillRect(p.x * CELL_SIZE + 1, p.y * CELL_SIZE + 1, CELL_SIZE - 2, CELL_SIZE - 2);
            idx++;
        }
    }

    private void drawApple(Graphics2D g) {
        if (engine.goldenApple) {
            g.setColor(Color.YELLOW);
            g.fillOval(engine.apple.x * CELL_SIZE + 2, engine.apple.y * CELL_SIZE + 2,
                       CELL_SIZE - 4, CELL_SIZE - 4);
        } else {
            g.setColor(APPLE_COLOR);
            g.fillOval(engine.apple.x * CELL_SIZE + 2, engine.apple.y * CELL_SIZE + 2,
                       CELL_SIZE - 4, CELL_SIZE - 4);
        }
    }
    
    private void drawObstacles(Graphics2D g) {
        g.setColor(new Color(100, 80, 70));
        for (Point p : engine.obstacles) {
            g.fill3DRect(p.x * CELL_SIZE + 2, p.y * CELL_SIZE + 2, 
                        CELL_SIZE - 4, CELL_SIZE - 4, true);
        }
    }

    private void drawScore(Graphics2D g) {
        g.setColor(TEXT_COLOR);
        g.setFont(FONT_SCORE);
        g.drawString("Счёт: " + engine.score, 15, 30);
        g.drawString("Лучший: " + engine.highScore, 15, 60);
    }
    
    private void drawCombo(Graphics2D g) {
        if (engine.combo > 1) {
            g.setColor(new Color(255, 100, 255));
            g.setFont(FONT_SMALL);
            g.drawString("COMBO x" + engine.combo, WINDOW_WIDTH - 100, 40);
        }
    }

    private void drawOverlay(Graphics2D g, String title, String hint) {
        g.setColor(OVERLAY_COLOR);
        g.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        g.setColor(TEXT_COLOR);
        g.setFont(FONT_BIG);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, (WINDOW_WIDTH - fm.stringWidth(title)) / 2, WINDOW_HEIGHT / 2 - 30);
        g.setFont(FONT_SMALL);
        fm = g.getFontMetrics();
        g.drawString(hint, (WINDOW_WIDTH - fm.stringWidth(hint)) / 2, WINDOW_HEIGHT / 2 + 20);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();

        if (!engine.gameOver && !engine.paused) {
            int newDir = -1;
            if (key == KEY_UP) newDir = 3;
            else if (key == KEY_LEFT) newDir = 2;
            else if (key == KEY_DOWN) newDir = 1;
            else if (key == KEY_RIGHT) newDir = 0;

            if (newDir != -1) {
                engine.setDesiredDirection(newDir);
                return;
            }
        }

        switch (key) {
            case KEY_PAUSE:
                if (!engine.gameOver) engine.togglePause();
                break;
            case KEY_NEW_GAME:
                engine.newGame();
                break;
            case KEY_EXIT:
                int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Вы уверены, что хотите выйти из игры?",
                    "Подтверждение выхода",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
                );
                if (confirm == JOptionPane.YES_OPTION) {
                    engine.stopGame();
                    System.exit(0);
                }
                break;
        }
    }

    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}
}

/** Точка входа **/
public class SnakeGame {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Snake — WASD | Фиолетовая змейка");
            GamePanel panel = new GamePanel();
            frame.add(panel);
            frame.pack();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLocationRelativeTo(null);
            frame.setResizable(false);
            frame.setVisible(true);
            panel.requestFocusInWindow();
        });
    }
}