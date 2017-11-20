# About

## Architecture

### Raw News Consumer

The system responsible for performing aggregation and collection of raws news items can be found in `src/main/scala/com/example/actor/RedditClientActor.scala`. The Actor system found within is responsible for handling authentication to the Reddit API, rate throttling, and pagination through the subreddits that are targeted for evaluation. The system can be launched by providing it with a list of subreddits to process. By periodically telling this system to refetch those subreddits, the system can continue to accumulate new items over time for evaluation. The system also stores the raw content for later re-evaluation, if needed.

### Processor

The evaluation system for the most popular content can be found in `src/main/scala/com/example/actor/ProccessorActor.scala`. The Processor system operates on raw news items as they are retrieved by the Client Actor. The Processor evaluates news items on the fly, adding them to a sorted collection, and, if the number of items exceeds the limit of most popular items to display, the least popular item is removed. To handle cases where a news item may change over time, the Processor also stores an indexed collection of the items it has seen, indexed by the ID of the item. If an item has been previously seen, the old item is removed from the collection before the new item added. An item is considered popular based on its overall score from Reddit; the higher the score, the more popular a given item is.

## Enhancements

### Scaling

Actor Systems are generally well-suited to scale out, as they operate as independent entities within the system. For example, if the Processor subsystem was a bottleneck overall in processing news entities, a routing system could be inserted to route messages to multiple processor actors and distribute the load.

However, the Reddit Client Actor is not currently well-suited to scale out. The system assumes a single Actor entity is making requests against the Reddit API, to manage rate-limit throttling, and to manage authentication. The system would need to be refactored in such a way that the rate limiting and authentication state is shared across API consumer Actors.

### Twitter

Due to time, and the lackluster nature of the public Twitter API, I chose not to use Twitter as a source for determining news popularity. The public APIs I reviewed didn't provide a clear mechanism to assess popularity within Twiter unless there were specific targeting mechanisms involved. With more time, I would consider two approaches to integrating Twitter content:

1. Using Twitter as a item enrichment source for calculating popularity. I.e., once a News item was found on Reddit, perform an additional search on Twitter to assess the URL's popularity outside of a single source. This result would be used to further rank the news items received from Reddit.
2. With access to the Twitter stream in full, Twitter could be used as an additional raw data source, with the Client actors processing the stream to find URLs that are commonly shared.

### Most Popular

The calculation of the score currently only takes the magnitude of score into account. This leads the system to consider an item 23 hours old with 10,000 upvotes no different than an item posted an hour ago with the same 10,000 likes. An additional score evaluation would take into account an items 'velocity', such that items that have quickly received a high magnitude of score are ranked higher in order than items which have the same magnitude over a longer period of time. A system like this might be overly sensitive to 'ballot-stuffing', and would need to be tuned to dampen the effects of voters trying to artificially increase the rank of a given item.

### Data Store

The current data store isn't well indexed, and is difficult to slice into interesting subsections (such as all the news about Technology, or Europe). A more refined system would provide these views into the data above and beyond the most popular sorting in the system currently.
