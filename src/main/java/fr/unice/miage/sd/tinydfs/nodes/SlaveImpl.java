package fr.unice.miage.sd.tinydfs.nodes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class SlaveImpl extends UnicastRemoteObject implements Slave {

	private int idSlave;
	private String dfsRootFolder;
	private Slave leftSlave, rightSlave;

	public SlaveImpl(int id, String dfsRootFolder) throws RemoteException {
		super();
		this.idSlave = id;
		this.dfsRootFolder = dfsRootFolder;
		File dfsFileRootFolder = new File(dfsRootFolder);
		if (!dfsFileRootFolder.exists()) {
			dfsFileRootFolder.mkdir();
			System.out.println("Création dossier " + dfsFileRootFolder.getName());
		} else {
			File[] oldFilesSlave = dfsFileRootFolder.listFiles(new filterSlave());
			for (File oldFile : oldFilesSlave) {
				System.out.println("Suppression du fichier " + oldFile.getName());
				oldFile.delete();
			}

		}
	}

	@Override
	public int getId() throws RemoteException {
		return idSlave;
	}

	@Override
	public Slave getLeftSlave() throws RemoteException {
		return leftSlave;
	}

	@Override
	public Slave getRightSlave() throws RemoteException {
		return rightSlave;
	}

	@Override
	public void setLeftSlave(Slave slave) throws RemoteException {
		leftSlave = slave;
	}

	@Override
	public void setRightSlave(Slave slave) throws RemoteException {
		rightSlave = slave;
	}

	@Override
	public void subSave(String filename, List<byte[]> subFileContent) throws RemoteException {
		int sizeList, middleList;
		middleList = (sizeList = subFileContent.size()) / 2;
		try {
			subSaveDisk(idSlave + filename, subFileContent.get(middleList));
		} catch (IOException e) {
			System.err.println("Erreur d'écriture du fichier " + idSlave + filename);
			e.printStackTrace();
		}
		if (middleList != 0) {
			List<byte[]> left = new ArrayList<byte[]>(subFileContent.subList(0, middleList));
			List<byte[]> right = new ArrayList<byte[]>(subFileContent.subList(middleList + 1, sizeList));
			leftSlave.subSave(filename, left);
			rightSlave.subSave(filename, right);
		}
	}
	
	@Override
	public long getFileSubsize(String filename) throws RemoteException {
		File f = new File(dfsRootFolder + File.separator + idSlave + filename);
		if(!f.exists() || f.isDirectory()) {
			System.out.println("Slave" + idSlave + "ta mere");
			return -1;
		}
		if(rightSlave == null) {
			return f.length();
		}
		long rightSize = rightSlave.getFileSubsize(filename);
		long leftSize = leftSlave.getFileSubsize(filename);
		long res = leftSize + f.length() + rightSize;
		return res;
	}

	private void subSaveDisk(String filename, byte[] fileContent) throws IOException {
		FileOutputStream stream = new FileOutputStream(dfsRootFolder + File.separator + filename);
		stream.write(fileContent);
		stream.close();
	}

	private byte[] subRetireveDisk(String filename) {
		Path path = Paths.get(dfsRootFolder + File.separator + filename);
		byte[] data = null;
		try {
			data = Files.readAllBytes(path);
		} catch (IOException e) {
			System.err.println("Erreur de lecture du fichier " + dfsRootFolder + File.separator + filename);
			e.printStackTrace();
		}
		return data;
	}

	@Override
	public List<byte[]> subRetrieve(String filename) throws RemoteException {
		if (!(new File(dfsRootFolder + File.separator + idSlave + filename)).exists()) {
			System.err.println("Le fichier " + idSlave + filename + " nexiste pas ... !");
			return null;
		}

		if (leftSlave == null) {
			return new ArrayList<>(Arrays.asList(subRetireveDisk(idSlave + filename)));
		}
		List<byte[]> responsableList = leftSlave.subRetrieve(filename);
		responsableList.add(subRetireveDisk(idSlave + filename));
		responsableList.addAll(rightSlave.subRetrieve(filename));
		return responsableList;
	}

	class filterSlave implements FilenameFilter {

		@Override
		public boolean accept(File folder, String name) {
			return Pattern.compile("^" + idSlave).matcher(name).matches();
		}

	}
}
