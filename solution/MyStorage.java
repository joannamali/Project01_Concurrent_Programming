package cp2023.solution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import cp2023.base.*;
import cp2023.exceptions.*;


public class MyStorage implements StorageSystem {
    
    private Map<DeviceId, Device> DeviceMap = new HashMap<>(); 
    private ReentrantLock BegginingProtocole = new ReentrantLock(true);
    private ReentrantLock ComponentLock = new ReentrantLock(true);
    private Set<ComponentId> BusyComponents = ConcurrentHashMap.newKeySet(); 
    public ConcurrentHashMap<ComponentId, DeviceId> AllComponents 
        = new ConcurrentHashMap<>();


    public MyStorage( Map<DeviceId, Integer> deviceTotalSlots,
        Map<ComponentId, DeviceId> componentPlacement) 
        throws IllegalArgumentException {
            if (deviceTotalSlots.isEmpty())
                throw new IllegalArgumentException
                ("At least one device is needed.");
            for(DeviceId id : deviceTotalSlots.keySet()){
                int slots = deviceTotalSlots.get(id); 
                if (id == null)
                    throw new IllegalArgumentException
                        ("Device id cannot be null.");
                if (slots <= 0)
                    throw new IllegalArgumentException
                        ("Number of slots must be positive.");
                Device d = new Device(id, slots, DeviceMap, AllComponents); 
                DeviceMap.put(id, d);
            }
            for (ComponentId id : componentPlacement.keySet()){
                DeviceId deviceId = componentPlacement.get(id); 
                if (!DeviceMap.containsKey(deviceId))
                    throw new IllegalArgumentException
                        ("Device does not exist.");
                
                Device dev = DeviceMap.get(deviceId);
                if (AllComponents.contains(id))
                    throw new IllegalArgumentException
                        ("Component already exists.");
                if (dev.freeSlots == 0)
                    throw new IllegalArgumentException
                        ("Device does not have free space.");

                AllComponents.put(id, deviceId); 
                dev.freeSlots--;   
            }    
    }

    // Checks all the exceptions.
    private void ExceptionCheck(ComponentTransfer T) throws TransferException {
        DeviceId sourceId = T.getSourceDeviceId();
        DeviceId destinationId = T.getDestinationDeviceId();
        ComponentId componentId = T.getComponentId();

        if (sourceId == null && destinationId == null)
            throw new IllegalTransferType(componentId); 

        if (sourceId != null && !DeviceMap.containsKey(sourceId))
            throw new DeviceDoesNotExist(sourceId);

        if (destinationId != null && !DeviceMap.containsKey(destinationId))
            throw new DeviceDoesNotExist(destinationId);

        if (sourceId != null && sourceId.equals(destinationId))
            throw new ComponentDoesNotNeedTransfer(componentId, destinationId);

        ComponentLock.lock();

        if (BusyComponents.contains(componentId))
            throw new ComponentIsBeingOperatedOn(componentId);

        if (sourceId == null && AllComponents.containsKey(componentId))
            throw new ComponentAlreadyExists(componentId, 
                AllComponents.get(componentId));    

        if (sourceId != null && (!AllComponents.containsKey(componentId) || 
             !AllComponents.get(componentId).equals(sourceId)))
            throw new ComponentDoesNotExist(componentId, sourceId);
        
        BusyComponents.add(componentId); 
        ComponentLock.unlock();

        
    }

    // Searches for cycle using recursion.
    private ArrayList<ComponentTransfer> Search4Cycle (Device start, 
        ComponentTransfer current, ArrayList<ComponentTransfer> path){

        DeviceId sourceId = current.getSourceDeviceId(); 
        if (sourceId == null)
            return new ArrayList<>();
        
        Device sourceDev = DeviceMap.get(sourceId);
        path.add(current);

        if (start == sourceDev && path.size() > 1){   // cycle found
            sourceDev.reservations.put(current, path.get(0));
            sourceDev.WaitingLine.remove(path.get(0));
            return path; 
        }
        for (ComponentTransfer T : sourceDev.WaitingLine){
            ArrayList<ComponentTransfer> result = Search4Cycle(start, T, path);
            if (!result.isEmpty()){
                sourceDev.reservations.put(current, T);
                sourceDev.WaitingLine.remove(T);
                sourceDev.WaitingRoom.get(T).signal();
                return result; 
            } 
        }
        return new ArrayList<>();
    }

    // Makes a reservation for its slot for the first waiting transfer.
    public void MakeReservations (ComponentTransfer T){
        DeviceId devId = T.getSourceDeviceId(); 
        if (devId == null)
            return; 
        Device dev = DeviceMap.get(devId); 
        
        if (dev.WaitingLine.isEmpty() && !dev.occupiedSlots.contains(T))
            dev.occupiedSlots.add(T); 

        else if (!dev.reservations.containsKey(T)){
            ComponentTransfer getter = dev.WaitingLine.poll(); 
            dev.reservations.put(T, getter); 
            dev.WaitingRoom.get(getter).signal();
            MakeReservations (getter); 
        }
    }

    @Override
    public void execute(ComponentTransfer transfer) throws TransferException {
        ExceptionCheck(transfer); 
        ComponentId componentId = transfer.getComponentId();
        DeviceId sourceId = transfer.getSourceDeviceId();
        DeviceId destinationId = transfer.getDestinationDeviceId();
        try {
            boolean add = false; 
            boolean delete = false; 
            ReentrantLock lockDest = null; 
            ReentrantLock lockSour = null; 
            if (destinationId == null){
                delete = true; 
            }
            if (sourceId == null){
                add = true; 
            }
            Device destination = DeviceMap.get(destinationId);
            Device source = DeviceMap.get(sourceId); 

            if (!delete) lockDest = destination.lock; 
            if (!add) lockSour = source.lock;
            
            BegginingProtocole.lock(); 
            for (Device dev : DeviceMap.values())
                dev.lock.lock(); 
            
            if (!delete) {
                if (destination.TransferAdd(transfer)){ 
                    Search4Cycle(source, transfer, new ArrayList<>());
                    for (Device dev : DeviceMap.values()){
                        if (dev != destination)
                            dev.lock.unlock(); 
                    }
                    BegginingProtocole.unlock(); 
                    destination.AfterCycleCheck(transfer); 
                    lockDest.unlock();
                }
                else {
                    if (!add) MakeReservations(transfer);   
                    for (Device dev : DeviceMap.values())
                            dev.lock.unlock(); 
                    BegginingProtocole.unlock(); 
                }  
            }
            else if (!add) {
                if (!add) MakeReservations(transfer);   
                for (Device dev : DeviceMap.values())
                        dev.lock.unlock(); 
                BegginingProtocole.unlock(); 
            }
            transfer.prepare(); 
            if (!add) {
                lockSour.lock(); 
                source.TransferLeave(transfer); 
                lockSour.unlock(); 
            }
            if (!delete){
                lockDest.lock(); 
                destination.TransferEnter(transfer);
                lockDest.unlock(); 
            }
            else 
                AllComponents.remove(componentId);

            transfer.perform();
            BusyComponents.remove(componentId); 
        }
        catch (InterruptedException e){
            throw new RuntimeException("panic: unexpected thread interruption");
        }
    }
}
