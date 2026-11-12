package app.aaps.testcategories

/**
 * Marks an instrumented test class as **shard B**: `-e annotation app.aaps.testcategories.ShardB`. See [ShardA] for
 * how the shards are split and what is in each.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ShardB
