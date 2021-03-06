import java.io.*;
import java.util.*;

import weka.core.Instance;
import weka.core.Instances;
import weka.core.Attribute;
import weka.core.OptionHandler;
import weka.classifiers.Classifier;
import weka.core.converters.ConverterUtils.DataSource;
import weka.core.converters.ArffSaver;
 
/**
 * This is a light wrapper around Weka.
 * @author Percy Liang
 */
public class Main {
  private static Instances readData(String path) {
    try {
      if (!new File(path).exists())
        throw new RuntimeException("Data file does not exist: " + path);
      Instances data = new DataSource(path).getDataSet();
      // Default: the last attribute is the class
      data.setClassIndex(data.numAttributes() - 1);
      System.out.println("Read " + path + ": " + data.numInstances() + " instances, " + data.numAttributes() + " attributes, " + data.numClasses() + " classes");
      return data;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void writeData(Instances data, String path) {
    try {
      ArffSaver saver = new ArffSaver();
      saver.setInstances(data);
      saver.setFile(new File(path));
      saver.writeBatch();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static Object newInstance(String name) {
    try {
      return Class.forName(name).newInstance();
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    } catch (InstantiationException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 3) {
      System.out.println("Usage:");
      System.out.println("  learn <input path (contains data.arff)> <output path (contains model/weka_classifier)> <classifier class (e.g., weka.classifiers.trees.J48)> [<other options>]");
      System.out.println("  predict <input (contains model/weka_classifier, data/data.arff)> <output path (contains predictions/predictions)> [<other options>]");
      System.out.println("  evaluate <input path (contains (data/data.arff, predictions/predictions)> <output path (contains evaluation)> [<other options>]");
      System.exit(1);
    }
    String mode = args[0];

    if (mode.equals("learn")) {
      String classifierName = args[1];
      String dataPath = args[2];
      String modelPath = args[3];

      // Get classifier
      Classifier classifier = (Classifier)newInstance(classifierName);
      String[] opts = new String[args.length-4];
      for (int i = 0; i < opts.length; i++) opts[i] = args[i+4];
      if (classifier instanceof OptionHandler)
        ((OptionHandler)classifier).setOptions(opts);

      // Get data
      Instances data = readData(dataPath);

      // Learn
      classifier.buildClassifier(data);
      weka.core.SerializationHelper.write(modelPath, classifier);
    } else if (mode.equals("predict")) {
      String modelPath = args[1];
      String dataPath = args[2];
      String predictionsPath = args[3];

      // Get classifier
      Classifier classifier = (Classifier)weka.core.SerializationHelper.read(modelPath);

      // Get data
      Instances data = readData(dataPath);

      // Predict
      PrintWriter out = new PrintWriter(new FileWriter(predictionsPath));
      Attribute outputAttr = data.classAttribute();
      for (int i = 0; i < data.numInstances(); i++) {
        double prediction = classifier.classifyInstance(data.instance(i));
        if (outputAttr.isNumeric())
          out.println(prediction);
        else if (outputAttr.isNominal())
          out.println(outputAttr.value((int)prediction));
        else
          throw new RuntimeException("Unsupported attribute type: + " + outputAttr);
      }
      out.close();
    } else if (mode.equals("evaluate")) {
      String truePath = args[1];
      String predPath = args[2];
      String statsPath = args[3];

      // Get data
      Instances data = readData(truePath);

      // Read predictions
      ArrayList<String> predictions = new ArrayList<String>();
      try {
        String line;
        BufferedReader in = new BufferedReader(new FileReader(predPath));
        while ((line = in.readLine()) != null) {
          predictions.add(line);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      // Compare
      Attribute outputAttr = data.classAttribute();
      if (predictions.size() != data.numInstances())
        throw new RuntimeException("Expected " + data.numInstances() + " predictions, but got " + predictions.size());
      double error = 0;
      for (int i = 0; i < data.numInstances(); i++) {
        Instance instance = data.instance(i);
        if (outputAttr.isNumeric()) {
          double residual = instance.classValue() - Double.parseDouble(predictions.get(i));
          error += residual * residual;
        } else if (outputAttr.isNominal()) {
          String trueOutput = outputAttr.value((int)instance.classValue());
          if (!trueOutput.equals(predictions.get(i)))
            error += 1;
        }
      }
      error /= data.numInstances();
      if (outputAttr.isNumeric()) error = Math.sqrt(error);  // To compute RMSE
      System.out.println("Evaluated " + data.numInstances() + " instances, error is " + error);

      PrintWriter out = new PrintWriter(statsPath);
      out.println("errorRate: " + error);
      out.println("numExamples: " + data.numInstances());
      out.close();
    } else if (mode.equals("stripLabels")) {
      String dataPath = args[1];
      String strippedDataPath = args[2];

      // Get data
      Instances data = readData(dataPath);

      // Remove class labels
      for (int i = 0; i < data.numInstances(); i++)
        data.instance(i).setMissing(data.classAttribute());

      // Output
      writeData(data, strippedDataPath);
    }
  }
}
