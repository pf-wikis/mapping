package io.github.pfwikis.layercompiler.description;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.github.dexecutor.core.Dexecutor;
import com.google.common.graph.ElementOrder;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;

import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import lombok.Getter;
import tools.jackson.dataformat.yaml.YAMLMapper;

public class ExecutionPlan {
	
	public static record Edge(String id, StepDescription from, StepDescription to) {}
	
	private Map<String, StepDescription> id2Step = new HashMap<>();
	@Getter
	private MutableNetwork<StepDescription, Edge> graph = NetworkBuilder.directed()
        	.allowsParallelEdges(false)
        	.allowsSelfLoops(false)
        	.edgeOrder(ElementOrder.insertion())
        	.nodeOrder(ElementOrder.insertion())
        	.build();

	public static ExecutionPlan parseFromFile() {
        var lsDescriptions = new YAMLMapper().readValue(new File("steps.yaml"), LCDescription[].class);
        var plan = new ExecutionPlan();
        
        Map<LCDescriptionStep, StepDescription> raw2Step = new HashMap<>();
        
        
        //create all steps
        for(var lsDescription : lsDescriptions) {
        	for(int i=0;i<lsDescription.getSteps().size();i++) {
                var rawStep = lsDescription.getSteps().get(i);
                String id = lsDescription.createStepId(i);
                if(plan.id2Step.containsKey(id)) {
                    throw new IllegalStateException("Duplicate name "+id);
                }
                var descr = new StepDescription(id, lsDescription.getName(), rawStep.getStep(), rawStep.createStep());
                descr.getExecutor().setDescription(descr);
                plan.id2Step.put(id, descr);
                raw2Step.put(rawStep, descr);
                plan.graph.addNode(descr);
        	}
        }
     
        //create all edges
        for(var lsDescription : lsDescriptions) {
        	for(int i=0;i<lsDescription.getSteps().size();i++) {
        		var rawStep = lsDescription.getSteps().get(i);
        		var descr = raw2Step.get(rawStep);
                //steps in a group depend on their previous step
                if(i>0) {
                	var prev = plan.getStep(lsDescription.createStepId(i-1));
                	addEdge(plan, prev, descr, "in");
                }

                for(var e:rawStep.getDependsOn().entrySet()) {
                	var dep = e.getValue();
                	//meaning the last entry in the given group
                	if(!dep.contains(".")) {
                		var oldDep = dep;
                		var group = Arrays.stream(lsDescriptions).filter(lsd->lsd.getName().equals(oldDep)).findAny().get();
                		dep = group.createStepId(group.getSteps().size()-1);
                	}
                	addEdge(plan, plan.getStep(dep), descr, e.getKey());
                }
            }
        }
        
        for(var step:plan.graph.nodes()) {
			var ann = step.getExecutor().getClass().getDeclaredAnnotation(Time.Requirement.class);
			step.setTimeRequirement(ann.value());
		}
        
        return plan;
	}
	
	/*
	 
	 synthetic steps
	 
	 
	 //Create checking step for each reading
    if(rawStep.getStep().equals("READ_FILE")) {
    	var checker = new CheckGeometry();
    	checker.init(ctx);
    	checker.setName(lsDescription.getName()+"-check-geometry");
    	checker.setStep("CHECK_GEOMETRY");
    	checker.setLayer(((ReadFile)lcstep).getLayer());
    	checker.getInputMapping().put("in", id);
    	steps.put(id+"-check-geometry", checker);
    	executor.addIndependent(id+"-check-geometry");
    	executor.addDependency(id, id+"-check-geometry");
    }
	 
	 
	 */

	private static void addEdge(ExecutionPlan plan, StepDescription from, StepDescription to, String id) {
		plan.graph.addEdge(from, to, new Edge(id, from, to));
		to.getExecutor().getInputMapping().put(id, from.getId());
	}
	
	public StepDescription getStep(String id) {
		return Objects.requireNonNull(id2Step.get(id), "Could not resolve step "+id);
	}

	public void createExecutions(Dexecutor<String, Content> executor) {
		for(var n:graph.nodes()) {
			executor.addIndependent(n.getId());
		}
		for(var e:graph.edges()) {
			executor.addDependency(e.from.getId(), e.to.getId());
		}
	}
}
