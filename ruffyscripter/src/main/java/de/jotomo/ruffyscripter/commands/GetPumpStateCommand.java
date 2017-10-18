package de.jotomo.ruffyscripter.commands;

import org.monkey.d.ruffy.ruffy.driver.display.MenuType;

import java.util.Collections;
import java.util.List;

import de.jotomo.ruffy.spi.CommandResult;

import static de.jotomo.ruffyscripter.commands.GetPumpStateCommand.Stepper.runStep;

public class GetPumpStateCommand extends BaseCommand {
    interface Step {
        void doStep();
    }

    static boolean cancelRequested;


    public static class Stepper {
        public static void runStep(int retries, String desc, Step step) {
            runStep(retries, desc, step, null, null);
        }
        public static void runStep(int retries, String desc, Step step, String recoveryDesc, Step recovery) {
            // pre-checks here
            if (cancelRequested) {
//                runStep(0, "return to neutral state", () -> scripter.navigateToMainMenu());
//                if (recovery != null) recovery.doStep();
//                throw new CommandAbortedException(true);
            }
            if (true /*conectionLost*/) {
                // either try to reconnect and deal with raised alarms (corfirm them and forward to AAPS)
                // or let RS know he should reconnect and handle it (if that routine handles
                // recovery in a generic way
            }
            /*boolean success/result =*/ step.doStep();
            if (true /*successful*/) {
                //
            } else {
                runStep(retries - 1, desc, step, recoveryDesc, recovery);
            }
        }
    }

    static class StepBuilder {
        public StepBuilder(String desc) {}

        public String result;

        Step cancel = new Step() {
            @Override
            public void doStep() {
                // default recovery
            }
        };

        public StepBuilder retries(int retries) { return this; }
        public StepBuilder description(Step step) {
            return  this;
        }
        public StepBuilder step(Step step) {
            return  this;
        }
        public StepBuilder recover(Step step) {
            return  this;
        }
        public StepBuilder cancel(Step step) {
            return  this;
        }
        public StepBuilder failure(Step step) { return this; }
        public StepBuilder run() {
            return this;
        }
    }



    // state/info on whether an abort in that situtaion will raise a pump alert that we need
    // to connect to the pump for quickly and dismiss it


//    void step(String description, Code c) {
//        c.run();
//
//        exception/unexpected state
//        user requested cancel
//        disconnect info from ruffy
//
//    }

    public CommandResult execute2() {

        new StepBuilder("Navigate to bolus menu") // turn into a method createStep() or so, which has access to the scripter
                .step(new Step() {
                    @Override
                    public void doStep() {
                        System.out.println("something");
                    }
                })
                .recover(new Step() {
                    @Override
                    public void doStep() {
                        System.out.println("default impl: navigate back to main menu, no alarms");
                    }
                })
                .run();
        new StepBuilder("Input bolus") // turn into a method createStep() or so, which has access to the scripter
                .retries(5)
                .failure(new Step() {
                    @Override
                    public void doStep() {
                        System.out.println("retry command");
                    }
                })
                .step(new Step() {
                    @Override
                    public void doStep() {
                        System.out.println("something");
                    }
                })
                .recover(new Step() {
                    @Override
                    public void doStep() {
                        System.out.println("navigate back and cancel 'bolus cancelled' alert");
                    }
                })
                .run();
        // ^^ would allow overriding a default recovery or abort method
        // vv below code as well, with varargs and overloading or simply more methods like runStepWithCustomRecovery
        runStep(0, "check things", new Step() {
            @Override
            public void doStep() {
                scripter.navigateToMenu(MenuType.MY_DATA_MENU);
            }
        });
        runStep(0, "check things", new Step() {
            @Override
            public void doStep() {
                scripter.navigateToMenu(MenuType.MY_DATA_MENU);
            }
        }, "recover by doing x", new Step() {
            @Override
            public void doStep() {
                // recover
            }
        });

        return null;
    }

    @Override
    public CommandResult execute() {
        return new CommandResult().success(true).enacted(false).message("Returning pump state only");
    }

//    @Override
//    public CommandResult execute() {
//        return new CommandResult().success(true).enacted(false).message("Returning pump state only");
//    }

    @Override
    public List<String> validateArguments() {
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return "ReadPumpStateCommand{}";
    }
}
