import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;

/**
 * Gravity Playground - main window and controls.
 *
 * Compile:  javac *.java
 * Run:      java GravityPlayground
 */
public class GravityPlayground extends JFrame {
    private SimPanel panel = new SimPanel();
    private Simulation sim = panel.getSimulation();

    public GravityPlayground() {
        super("Gravity Playground");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
        add(buildControls(), BorderLayout.WEST);
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel buildControls() {
        JPanel c = new JPanel();
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        c.setPreferredSize(new Dimension(250, 700));

        // --- pause / clear ---
        JButton pause = new JButton("Pause");
        pause.addActionListener(e -> {
            panel.setPaused(!panel.isPaused());
            pause.setText(panel.isPaused() ? "Resume" : "Pause");
        });
        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> sim.clear());
        JPanel buttons = new JPanel();
        buttons.add(pause);
        buttons.add(clear);
        add(c, buttons);

        // --- presets ---
        add(c, new JLabel("Presets"));
        String[] presetNames = { "Solar system", "Binary star", "Figure-eight", "Chaos cluster" };
        JComboBox<String> presets = new JComboBox<String>(presetNames);
        JButton load = new JButton("Load preset");
        load.addActionListener(e -> panel.loadPreset((String) presets.getSelectedItem()));
        add(c, presets);
        add(c, load);
        c.add(Box.createVerticalStrut(10));

        // --- integrator ---
        add(c, new JLabel("Integrator (try switching mid-orbit!)"));
        JComboBox<String> integrator = new JComboBox<String>(
                new String[] { "Velocity Verlet (good)", "Euler (naive)" });
        integrator.addActionListener(e -> sim.setUseVerlet(integrator.getSelectedIndex() == 0));
        add(c, integrator);
        c.add(Box.createVerticalStrut(10));

        // --- sliders ---
        JLabel massLabel = new JLabel("New body mass: 10");
        JSlider mass = new JSlider(0, 40, 10); // mass = 10^(value/10), from 1 to 10000
        mass.addChangeListener(e -> {
            double m = Math.pow(10, mass.getValue() / 10.0);
            panel.setNewMass(m);
            massLabel.setText("New body mass: " + Math.round(m) + (m >= 1000 ? "  (star!)" : ""));
        });
        add(c, massLabel);
        add(c, mass);

        JLabel dtLabel = new JLabel("Time step dt: 0.25");
        JSlider dt = new JSlider(1, 100, 25);
        dt.addChangeListener(e -> {
            double v = dt.getValue() / 100.0;
            sim.setDt(v);
            dtLabel.setText("Time step dt: " + v);
        });
        add(c, dtLabel);
        add(c, dt);

        JLabel stepsLabel = new JLabel("Steps per frame: 4");
        JSlider steps = new JSlider(1, 20, 4);
        steps.addChangeListener(e -> {
            panel.setStepsPerFrame(steps.getValue());
            stepsLabel.setText("Steps per frame: " + steps.getValue());
        });
        add(c, stepsLabel);
        add(c, steps);

        JLabel softLabel = new JLabel("Softening: 3.0");
        JSlider soft = new JSlider(0, 100, 30);
        soft.addChangeListener(e -> {
            double v = soft.getValue() / 10.0;
            sim.setSoftening(v);
            softLabel.setText("Softening: " + v);
        });
        add(c, softLabel);
        add(c, soft);
        c.add(Box.createVerticalStrut(8));

        // --- checkboxes ---
        JCheckBox vel = new JCheckBox("Velocity vectors (green)", true);
        vel.addActionListener(e -> panel.setShowVelocity(vel.isSelected()));
        JCheckBox acc = new JCheckBox("Acceleration vectors (red)", false);
        acc.addActionListener(e -> panel.setShowAccel(acc.isSelected()));
        JCheckBox trails = new JCheckBox("Trails", true);
        trails.addActionListener(e -> panel.setShowTrails(trails.isSelected()));
        JCheckBox merge = new JCheckBox("Merge on collision", true);
        merge.addActionListener(e -> sim.setMergeEnabled(merge.isSelected()));
        add(c, vel);
        add(c, acc);
        add(c, trails);
        add(c, merge);
        c.add(Box.createVerticalStrut(12));

        // --- the equations ---
        add(c, new JLabel("<html><b>The whole universe:</b><br>"
                + "a = &Sigma; G&middot;m<sub>j</sub>&middot;(r<sub>j</sub> &minus; r<sub>i</sub>) / (|r|&sup2; + &epsilon;&sup2;)<sup>3/2</sup><br>"
                + "v &larr; v + a&middot;dt<br>"
                + "x &larr; x + v&middot;dt</html>"));
        return c;
    }

    /** Small helper: add a component left-aligned so the layout looks tidy. */
    private void add(JPanel parent, Component comp) {
        if (comp instanceof javax.swing.JComponent) {
            ((javax.swing.JComponent) comp).setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        parent.add(comp);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GravityPlayground window = new GravityPlayground();
            window.setVisible(true);
            window.panel.loadPreset("Solar system");
        });
    }
}
