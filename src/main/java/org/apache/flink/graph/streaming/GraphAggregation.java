package org.apache.flink.graph.streaming;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.FoldFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.graph.Edge;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.util.Collector;

/**
 * @param <K>  key type
 * @param <EV> edge value type
 * @param <S>  intermediate state type
 * @param <T>  fold result type
 */
public abstract class GraphAggregation<K, EV, S, T> {

    /**
     * A function applied to each edge in an edge stream that aggregates a user-defined graph property state. In case
     * we slice the edge stream into windows a fold will output its aggregation state value per window, otherwise, this
     * operates edge-wise
     */
    private final FoldFunction<Edge<K, EV>, S> updateFun;

    /**
     * An optional combine function for updating graph property state
     */
    private final ReduceFunction<S> combineFun;

    /**
     * An optional merge function that converts state to ouput
     */
    private final MapFunction<S, T> mergeFun;

    private final S initialValue;

    /**
     * This flag indicates whether a merger state is cleaned up after an operation
     */
    private final boolean transientState;

    protected GraphAggregation(FoldFunction<Edge<K, EV>, S> updateFun, ReduceFunction<S> combineFun, MapFunction<S, T> mergeFun, S initialValue, boolean transientState) {
        this.updateFun = updateFun;
        this.combineFun = combineFun;
        this.mergeFun = mergeFun;
        this.initialValue = initialValue;
        this.transientState = transientState;
    }


    public abstract DataStream<T> run(DataStream<Edge<K, EV>> edgeStream);


    public ReduceFunction<S> getCombineFun() {
        return combineFun;
    }

    public FoldFunction<Edge<K, EV>, S> getUpdateFun() {
        return updateFun;
    }

    public MapFunction<S, T> getMergeFun() {
        return mergeFun;
    }

    public boolean isTransientState() {
        return transientState;
    }

    public S getInitialValue() {
        return initialValue;
    }

    protected FlatMapFunction<S, S> getAggregator(final DataStream<Edge<K, EV>> edgeStream) {
        return new FlatMapFunction<S, S>() {

            private final int numTasks = edgeStream.getParallelism();
            private final S initialVal = getInitialValue();

            private int toAggretate = numTasks;
            private S currentState = initialVal;

            @Override
            public void flatMap(S s, Collector<S> collector) throws Exception {
                if (combineFun != null) {
                    currentState = getCombineFun().reduce(currentState, s);
                    toAggretate--;

                    if (toAggretate == 0) {
                        collector.collect(currentState);
                        toAggretate = numTasks;
                    }

                    if (isTransientState()) {
                        currentState = initialVal;
                    }
                } else {
                    collector.collect(s);
                }
            }
        };
    }
}