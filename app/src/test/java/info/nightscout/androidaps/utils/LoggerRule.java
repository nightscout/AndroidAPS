package info.nightscout.androidaps.utils;

/**
 * Created by andy on 3/10/19.
 */

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

//import ch.qos.logback.core.read.ListAppender;

public class LoggerRule implements TestRule {

    @Override
    public Statement apply(Statement base, Description description) {
        return null;
    }

    /*
     * private final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
     * private final Logger logger = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
     * 
     * 
     * @Override
     * public Statement apply(Statement base, Description description) {
     * return new Statement() {
     * 
     * @Override
     * public void evaluate() throws Throwable {
     * setup();
     * base.evaluate();
     * teardown();
     * }
     * };
     * }
     * 
     * 
     * private void setup() {
     * logger.addAppender(listAppender);
     * listAppender.start();
     * }
     * 
     * 
     * private void teardown() {
     * listAppender.stop();
     * listAppender.list.clear();
     * logger.detachAppender(listAppender);
     * }
     * 
     * 
     * public List<String> getMessages() {
     * return listAppender.list.stream().map(e -> e.getMessage()).collect(Collectors.toList());
     * }
     * 
     * 
     * public List<String> getFormattedMessages() {
     * return listAppender.list.stream().map(e -> e.getFormattedMessage()).collect(Collectors.toList());
     * }
     */

}
